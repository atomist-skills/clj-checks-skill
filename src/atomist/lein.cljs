(ns atomist.lein
  (:require [rewrite-clj.zip :as z]
            [cljs-node-io.core :refer [slurp]]
            [cljs.reader :refer [read-string]]
            [goog.string.format]
            [cljs-node-io.core :as io]))

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

(defn dependencies
  ([zipper]
   (-> zipper
       z/down
       (z/find-next-value :dependencies)
       z/right)))

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
