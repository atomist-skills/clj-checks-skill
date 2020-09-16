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
     (assoc-in [:data :Push 0 :after :sha] "bf3d9f901bb223a0f8639cff42c3018def1058ed")
     (lr/add-configuration {:name "default" :parameters [{:name "clj-classpath-duplicates"
                                                          :value true}]})
     (lr/container-contract atomist.main/handler))))
