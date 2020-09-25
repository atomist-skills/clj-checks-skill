(ns atomist.checks.lib-spec
  (:require [cljs.core.async :refer [<!] :refer-macros [go]]
            [atomist.lein :as lein]
            [atomist.tools :as tools]
            [atomist.checks :as checks]
            [atomist.cljs-log :as log]
            [goog.string :as gstring]
            [cljs-node-io.core :as io]))

(defn symbol->name [s symbol]
  (gstring/format "%s-%s-%s" s (namespace symbol) (name symbol)))

(defn parse-lib-spec [s]
  (try
    (let [lib-spec (cljs.reader/read-string s)]
      [lib-spec (cond
                  (map? lib-spec) (-> lib-spec keys first ((partial symbol->name "lib-spec")))
                  (vector? lib-spec) (-> lib-spec first ((partial symbol->name "lib-spec"))))])
    (catch :default ex
      (log/warn "unable to parse lib-spec " s)
      nil)))

(defn extract-lib-specs [handler]
  (fn [request]
    (go
      (let [project-clj (io/file (:atmhome request) "project.clj")
            deps-edn (io/file (:atmhome request) "deps.edn")]
        (<! (handler (cond-> request
                       (.exists project-clj) (assoc :lein-specs (<! (lein/deps (io/slurp project-clj))))
                       (.exists deps-edn) (assoc :deps-specs (<! (tools/deps (io/slurp deps-edn)))))))))))

(defn run-check
  [request]
  ((-> (fn [request]
         (go (<! (cond-> request
                   (:lein-specs request) (lein/check-spec (:lein-specs request) (-> request :check :lib-spec))
                   (:deps-specs request) (tools/check-spec (:deps-specs request) (-> request :check :lib-spec))))))
       (extract-lib-specs))
   request))

(def check
  {:check-names (fn [request check]
                  (log/info "check " (:lib-spec request) (coll? (:lib-spec request)))
                  (if (coll? (:lib-spec request))
                    (->> (:lib-spec request)
                         (map #(let [[lib-spec lib-spec-check-name] (parse-lib-spec %)]
                                 (assoc check :check-name lib-spec-check-name :lib-spec lib-spec)))
                         (filter #(and (:check-name %) (:lib-spec %)))
                         (into []))
                    []))
   :title "Lib Specs"
   :run-check #'run-check})

