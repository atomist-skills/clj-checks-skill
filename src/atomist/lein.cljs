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

(ns atomist.lein
  (:require [rewrite-clj.zip :as z]
            [cljs-node-io.core :refer [slurp]]
            [cljs.reader :refer [read-string]]
            [goog.string.format]
            [cljs-node-io.core :as io]
            [cljs.core.async :refer-macros [go]]
            [goog.string :as gstring]
            [atomist.cljs-log :as log]))

(defn check-spec [request lib-specs target-spec]
  (go
    (if-let [off-target-spec
             (->> lib-specs
                  (filter #(and (= (first %) (first target-spec))
                                (not (= (second %) (second target-spec)))))
                  first)]
      (assoc request :checkrun/conclusion "action_required"
             :checkrun/actions [{:label "fix"
                                 :description "open a PR with a new Dependency"
                                 :identifier (str (hash target-spec))}]
             :checkrun/output {:title (-> request :check :title)
                               :summary (gstring/format "%s is at version %s.  Target is %s"
                                                        (first target-spec)
                                                        (second off-target-spec)
                                                        (second target-spec))})
      (assoc request :checkrun/conclusion "success"
             :checkrun/output {:title (-> request :check :title)
                               :summary "specs are in sync"}))))

(defn deps [s]
  (go
    (try
      (let [zipper (-> s
                       (z/of-string)
                       (z/down))
            dependencies (-> zipper (z/find-next-value :dependencies) (z/right) (z/sexpr))
            profiles (-> zipper (z/find-next-value :profiles) (z/right) (z/sexpr))]
        (reduce conj dependencies (->> profiles vals (mapcat :dependencies))))
      (catch :default _ []))))

(defn edit-library [s library-name library-version]
  (-> s
      (z/of-string)
      z/down
      (z/find-next-value :dependencies)
      (z/find z/next #(if-let [s (z/sexpr %)]
                        (and (symbol? s)
                             (= library-name (str s))
                             (= :vector (-> % z/prev z/node :tag)))))
      (z/right)
      (z/edit (constantly library-version))
      (z/root-string)))

(defn inject-dependency [s lib-specs]
  (-> s
      (z/of-string)
      z/down
      (z/find-next-value :dependencies)
      (z/right)
      (z/edit #(concat % lib-specs))
      (z/root-string)))

(defn get-version [f]
  (-> f
      (slurp)
      (read-string)
      (nth 2)))

(defn get-name [f]
  (-> f
      (slurp)
      (read-string)
      (nth 1)
      (str)))
