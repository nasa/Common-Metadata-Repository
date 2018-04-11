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
