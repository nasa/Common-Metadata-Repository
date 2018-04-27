(ns cmr.opendap.ous.util
  (:require
   [clojure.string :as string]))

(defn normalize-param
  [param]
  (-> param
      name
      (string/replace "_" "-")
      (string/lower-case)
      keyword))

(defn normalize-params
  [params]
  (->> params
       (map (fn [[k v]] [(normalize-param k) v]))
       (into {})))

(defn ->seq
  [data]
  (cond (nil? data) []
        (empty? data) []
        (coll? data) data
        (string? data) (string/split data #",")))

(defn bounding-box->subset
  [[ll-lon ll-lat ur-lon ur-lat]]
  [(format "lat(%s,%s)" ll-lat ur-lat)
   (format "lon(%s,%s)" ll-lon ur-lon)])

(defn parse-subset
  [subset]
  )

(defn subset->bounding-box
  [elems]
  )

(defn coverage->granules
  [coverage]
  (let [ids (filter #(string/starts-with? % "G") coverage)]
    (if (empty? ids)
      nil
      ids)))

(defn coverage->collection
  [coverage]
  (let [id (filter #(string/starts-with? % "C") coverage)]
    (if (empty? id)
      nil
      (first id))))
