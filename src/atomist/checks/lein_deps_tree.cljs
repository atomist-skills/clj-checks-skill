;; Copyright © 2020 Atomist, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

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

