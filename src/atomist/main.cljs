(ns atomist.main
  (:require [atomist.api :as api]
            [atomist.cljs-log :as log]
            [atomist.container :as container]
            [cljs-node-io.core :as io]
            [cljs.pprint :refer [pprint]]
            [goog.string.format]
            [atomist.checks.clj-classpath-duplicates :as clj-class-path-duplicates]
            [atomist.checks.lib-spec :as lib-spec]
            [atomist.checks.lein-deps-tree :as lein-deps-tree]
            [cljs.core.async :as async :refer [<! >! chan timeout] :refer-macros [go]]))

(defn run-check [handler]
  (fn [request]
    (go
      (<! (handler (<! ((-> request :check :run-check) request)))))))

;; wait for each check to complete - setup check-run handler
(defn run-checks [handler]
  (fn [request]
    (go
      (log/info "run checks " (:checks request))
      (<! (handler (assoc request :results (<! (->>
                                                (for [check (:checks request)]
                                                  ((-> #(go %)
                                                       (run-check)
                                                       (api/with-github-check-run :name (:check-name check)))
                                                   (assoc request :check check)))
                                                (async/merge)
                                                (async/reduce conj [])))))))))

;; scan configuration and setup checks
(defn setup-checks [handler]
  (fn [request]
    (go
      (<! (handler
           (assoc request
                  :checks (->> [lein-deps-tree/check lib-spec/check clj-class-path-duplicates/check]
                               (mapcat (fn [{:keys [check-names] :as check}]
                                         (->> (check-names request check)
                                              (into []))))
                               (into []))))))))

(defn cancel-if-not-clojure [handler]
  (fn [request]
    (go
      (let [atmhome (io/file (.. js/process -env -ATOMIST_HOME))]
        (if (.exists atmhome)
          (if (or (.exists (io/file atmhome "project.clj"))
                  (.exists (io/file atmhome "deps.edn")))
            (<! (handler (assoc request :atmhome atmhome)))
            (<! (api/finish request :success "Skipping non-clojure project" :visibility :hidden)))
          (do
            (log/warn "there was no checked out " (.getPath atmhome))
            (<! (api/finish request :failure "Failed to checkout"))))))))

(defn perform-requested-action [handler]
  (fn [request]
    (go
      (api/trace (str "perform requested action " (:action-identifier request)))
      (<! (handler request)))))

(defn check-run-action [handler]
  (fn [request]
    (go
      (log/info "check-run-action:  " (-> request :data :CheckRun))
      (if (= "requested_action" (-> request :data :CheckRun first :action))
        (<! (handler (assoc request :action-identifier (-> request :data :CheckRun first :requestedActionIdentifier))))
        (<! (api/finish request :visibility :hidden))))))

(def on-push
  (-> (api/finished :message "----> Push event handler finished"
                    :success "completed")
      (run-checks)
      (setup-checks)))

(def on-check-run
  (-> (api/finished :message "----> CheckRun event handler finished"
                    :success "completed")
      (perform-requested-action)
      (api/clone-ref)
      (check-run-action)))

(defn ^:export handler
  "no arguments because this handler runs in a container that should fulfill the Atomist container contract
   the context is extract fro the environment using the container/mw-make-container-request middleware"
  []
  ((-> (api/dispatch {:OnAnyPush on-push
                      :OnCheckRun on-check-run})
       (cancel-if-not-clojure)
       (api/extract-github-token)
       (api/add-skill-config)
       (api/create-ref-from-event)
       (api/skip-push-if-atomist-edited)
       (api/status)
       (container/mw-make-container-request))
   {}))

(comment
  (require '[atomist.local-runner :as lr])
  (do
    (lr/set-env :prod-github-auth)
    (->
     (lr/fake-push
      "AEIB5886C"
      "slimslender"
      {:name "clj2" :id "AEIB5886C_AEIB5886C_slimslender_133574647"}
      "master")
     (assoc-in [:data :Push 0 :after :sha] "47ddf9aed42e8aa1e962c7a94b1fe379b05c296d")
     (lr/add-configuration {:name "default"
                            :parameters [{:name "lib-spec"
                                          :value ["[metosin/compojure-api \"1.1.14\"]"]}]})
     (lr/container-contract handler))))
