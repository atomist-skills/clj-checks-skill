(ns atomist.local
  (:require [atomist.main]
            [atomist.local-runner :as lr]))

(comment
  (do
    (lr/set-env :prod-github-auth)
    (->
     (lr/fake-push "AEIB5886C" "slimslender" {:name "clj1" :id "AEIB5886C_AEIB5886C_slimslender_132627478"} "master")
     (assoc-in [:data :Push 0 :after :sha] "ba124b0232a2101e7b2e61c02976729c3c06445d")
     (lr/add-configuration {:name "default" :parameters [{:name "clj-classpath-duplicates"
                                                          :value true}]})
     (lr/container-contract atomist.main/handler))))
