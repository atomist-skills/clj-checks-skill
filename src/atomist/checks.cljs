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

(ns atomist.checks
  (:require [cljs.core.async :refer [<! >! chan timeout] :refer-macros [go]]
            [cljs-node-io.core :as io]
            [cljs-node-io.proc :as proc]
            [atomist.cljs-log :as log]
            [atomist.lein :as lein]))

(defn run-clojure-jvm-process [handler]
  (fn [request]
    (go
      (if-let [args (-> request :args)]
        (let [atmhome (:atmhome request)
              [err stdout stderr] (<! (proc/aexecFile (first args) (rest args) {:cwd (.getPath atmhome)
                                                                                :env (merge
                                                                                      {"_JAVA_OPTIONS" (str "-Duser.home=" (.getPath atmhome))}
                                                                                      (if (-> request :maven :username)
                                                                                        {"ARTIFACTORY_USER" (-> request :maven :username)
                                                                                         "MVN_ARTIFACTORYMAVENREPOSITORY_USER" (-> request :maven :username)})
                                                                                      (if (-> request :maven :password)
                                                                                        {"ARTIFACTORY_PWD" (-> request :maven :password)
                                                                                         "MVN_ARTIFACTORYMAVENREPOSITORY_PWD" (-> request :maven :password)}))}))]
          (cond
            err
            (<! (handler (assoc request
                                :checkrun/conclusion "failure"
                                :checkrun/output {:title (-> request :check :title)
                                                  :summary ((-> request :check :summary :failure) (. err -code) stdout stderr)})))

            (and (-> request :check :error?)
                 ((-> request :check :error?) (. err -code) stdout stderr))
            (<! (handler (assoc request
                                :checkrun/conclusion "failure"
                                :checkrun/output {:title (-> request :check :title)
                                                  :summary ((-> request :check :summary :failure) (. err -code) stdout stderr)})))

            :else
            (<! (handler (assoc request
                                :checkrun/conclusion "success"
                                :checkrun/output {:title (-> request :check :title)
                                                  :summary (-> request :check :summary :success)})))))
        (<! (handler request))))))

(defn inject-lein-classpath [handler]
  (fn [request]
    (go
      (let [f (io/file (:atmhome request) "project.clj")]
        (when (and (-> request :check :lein-classpath)
                   (.exists f))
          (log/info "inject lein dependency")
          (let [edited (lein/inject-dependency (io/slurp f) (-> request :check :lein-classpath))]
            (io/spit f edited))))
      (<! (handler request)))))
