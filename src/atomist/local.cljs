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

(ns atomist.local
  (:require [atomist.main]
            [atomist.local-runner :as lr]))

(comment
  (do
    (lr/set-env :prod-github-auth)
    (->
     #_(lr/fake-push "AEIB5886C" "slimslender" {:name "clj1" :id "AEIB5886C_AEIB5886C_slimslender_132627478"} "master")
     (lr/fake-push "AEIB5886C" "slimslender" {:name "slimslender.core" :id "AEIB5886C_AEIB5886C_slimslender_295928820"} "master")
     #_(assoc-in [:data :Push 0 :after :sha] "")
     (assoc-in [:data :Push 0 :after :sha] "3cb987ea7cf83c886dbf446cfa71aa99f1a92325")
     (lr/add-configuration {:name "default" :parameters [{:name "clj-classpath-duplicates"
                                                          :value true}
                                                         {:name "lib-spec"
                                                          :value ["{org.clojure/clojure {:mvn/version \"1.10.2\"}}"]}]})
     (lr/container-contract atomist.main/handler))))
