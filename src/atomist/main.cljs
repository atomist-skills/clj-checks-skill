(ns atomist.main
  (:require [atomist.api :as api]
            [atomist.cljs-log :as log]
            [atomist.container :as container]
            [atomist.lein :as lein]
            [cljs-node-io.core :as io]
            [cljs-node-io.proc :as proc]
            [cljs.pprint :refer [pprint]]
            [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :as async :refer [<! >! chan timeout] :refer-macros [go]]))

(defn inject-lein-classpath [f libspecs]
  (go
    (let [edited (lein/inject-dependency (io/slurp f) libspecs)]
      (io/spit f edited))))

(defn run-check [handler]
  (fn [request]
    (go
      (let [atmhome (io/file (.. js/process -env -ATOMIST_HOME))
            args (cond
                   (.exists (io/file atmhome "project.clj")) (-> request :check :lein-args)
                   (.exists (io/file atmhome "deps.edn")) (-> request :check :deps-args))]

        (when (and
               (.exists (io/file atmhome "project.clj"))
               (-> request :check :lein-classpath))
          (log/info "inject lein classpath")
          (<! (inject-lein-classpath (io/file atmhome "project.clj") (-> request :check :lein-classpath))))

        (log/info "run " args)
        (let [[err stdout stderr] (<! (proc/aexecFile (first args) (rest args) {:cwd (.getPath atmhome)
                                                                                :env {}}))]
          (cond
            err
            (<! (handler (assoc request
                                :checkrun/conclusion "failure"
                                :checkrun/output {:title (-> request :check :name)
                                                  :summary ((-> request :check :summary :failure) (. err -code) stdout stderr)})))

            :else
            (<! (handler (assoc request
                                :checkrun/conclusion "success"
                                :checkrun/output {:title (-> request :check :name)
                                                  :summary (-> request :check :summary :success)})))))))))

(defn run-checks [handler]
  (fn [request]
    (go
      (<! (handler (assoc request :results (<! (->>
                                                (for [check (:checks request)]
                                                  ((-> #(go %)
                                                       (run-check)
                                                       (api/with-github-check-run :name (:name check)))
                                                   (assoc request :check check)))
                                                (async/merge)
                                                (async/reduce conj [])))))))))

(def checks {:clj-classpath-duplicates {:name "clj-classpath-duplicates"
                                        :deps-args ["clojure" "-Sdeps" "{:deps {io.dominic/clj-classpath-duplicates {:mvn/version \"0.1.1\"}}}" "-m" "io.dominic.clj-classpath-duplicates.core"]
                                        :lein-classpath '[[io.dominic/clj-classpath-duplicates "0.1.1"]]
                                        :lein-args ["lein" "run" "-m" "io.dominic.clj-classpath-duplicates.core"]
                                        :summary {:success "no duplicates found"
                                                  :failure (fn [code stdout stderr]
                                                             (->>
                                                              [(gstring/format "found %d duplicates" code) ""]
                                                              (concat [(str stderr)])
                                                              (interpose "\n")
                                                              (apply str)))}}})

(defn setup-checks [handler]
  (fn [request]
    (go
      (<! (handler (assoc request :checks (->> #{:clj-classpath-duplicates}
                                               (map #(checks %))
                                               (filter identity)
                                               (into []))))))))

(defn cancel-if-not-clojure [handler]
  (fn [request]
    (go
      (let [atmhome (io/file (.. js/process -env -ATOMIST_HOME))]
        (if (.exists atmhome)
          (if (or (.exists (io/file atmhome "project.clj"))
                  (.exists (io/file atmhome "deps.edn")))
            (<! (handler request))
            (<! (api/finish request :success "Skipping non-clojure project" :visibility :hidden)))
          (do
            (log/warn "there was no checked out " (.getPath atmhome))
            (<! (api/finish request :failure "Failed to checkout"))))))))

(defn ^:export handler
  "no arguments because this handler runs in a container that should fulfill the Atomist container contract
   the context is extract fro the environment using the container/mw-make-container-request middleware"
  []
  ((-> (api/finished :message "----> Push event handler finished"
                     :success "completed")
       (run-checks)
       (setup-checks)
       (cancel-if-not-clojure)
       (api/extract-github-token)
       (api/add-skill-config)
       (api/create-ref-from-event)
       (api/skip-push-if-atomist-edited)
       (api/status)
       (container/mw-make-container-request))
   {}))
