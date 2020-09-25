(ns atomist.checks.clj-classpath-duplicates
  (:require [goog.string :as gstring]
            [goog.string.format]
            [atomist.checks :as checks]
            [cljs-node-io.core :as io]
            [cljs.core.async :refer-macros [go]]))

(defn run-check
  [request]
  ((-> #(go %)
       (checks/run-clojure-jvm-process)
       (checks/inject-lein-classpath))
   (assoc request :args (cond
                          (.exists (io/file (:atmhome request) "project.clj")) (-> request :check :lein-args)
                          (.exists (io/file (:atmhome request) "deps.edn")) (-> request :check :deps-args)))))

(def check
  {:check-names (fn [request check]
                  (if (:clj-classpath-duplicates request)
                    [(assoc check :check-name "clj-classpath-duplicates")]
                    []))
   :run-check #'run-check
   :title "Clj Classpath Duplicates"
   :deps-args ["clojure"
               "-Sdeps"
               "{:deps {io.dominic/clj-classpath-duplicates {:mvn/version \"0.1.1\"}}}"
               "-m"
               "io.dominic.clj-classpath-duplicates.core"]
   :lein-classpath '[[io.dominic/clj-classpath-duplicates "0.1.1"]]
   :lein-args ["lein"
               "run"
               "-m"
               "io.dominic.clj-classpath-duplicates.core"]
   :summary {:success "no duplicates found"
             :failure (fn [code _ stderr]
                        (->>
                         [(gstring/format "found %d duplicates" code) ""]
                         (concat [(str stderr)])
                         (interpose "\n")
                         (apply str)))}})

