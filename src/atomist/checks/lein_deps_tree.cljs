(ns atomist.checks.lein-deps-tree
  (:require [clojure.string :as str]
            [atomist.checks :as checks]
            [cljs.core.async :refer-macros [go]]))

(defn run-check
  [request]
  ((-> #(go %)
       (checks/run-clojure-jvm-process))
   (assoc request :args (-> request :check :lein-args))))

(def check
  {:check-names (fn [request check]
                  (if (:lein-deps-tree request)
                    [(assoc check :check-name "lein-deps-tree")]
                    []))
   :run-check #'run-check
   :title "Leiningen Deps Tree"
   :lein-args ["lein"
               "deps"
               ":tree-data"]
   :error? (fn [_ _ stderr]
             (str/includes? stderr "Possibly confusing dependencies found:"))
   :summary {:success "no confusing deps found"
             :failure (fn [_ _ stderr]
                        stderr)}})

