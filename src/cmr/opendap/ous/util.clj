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

(defn seq->str
  [data]
  (string/join "," data))

(defn bounding-box->subset
  [[lon-lo lat-lo lon-hi lat-hi]]
  [(format "lat(%s,%s)" lat-lo lat-hi)
   (format "lon(%s,%s)" lon-lo lon-hi)])

(defn get-matches
  [regex elems]
  (->> elems
       (map (comp rest (partial re-find regex)))
       (remove empty?)
       first))

(defn subset->bounding-lat
  [elems]
  (get-matches
   (re-pattern (str ".*lat\\("
                    "\\s*(-?[0-9]+\\.?[0-9]*)\\s*,"
                    "\\s*(-?[0-9]+\\.?[0-9]*)\\s*"))
   elems))

(defn subset->bounding-lon
  [elems]
  (get-matches
   (re-pattern (str ".*lon\\("
                    "\\s*(-?[0-9]+\\.?[0-9]*)\\s*,"
                    "\\s*(-?[0-9]+\\.?[0-9]*)\\s*"))
   elems))

(defn subset->bounding-box
  "In the CMR and EDSC, a bounding box is defined by the lower-left corner
  to the upper-right, furthermore, they defined this as a flattened list,
  ordering with longitude first. As such, a bounding box is of the form:
  `[lower-longitude, lower-latitude, upper-longitude, upper-latitude]`.

  This is the form that this function returns."
  [elems]
  (let [[lon-lo lon-hi] (subset->bounding-lon elems)
        [lat-lo lat-hi] (subset->bounding-lat elems)]
    (map #(Float/parseFloat %) [lon-lo lat-lo lon-hi lat-hi])))

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
