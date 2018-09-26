(ns cmr.exchange.query.util
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.exchange.query.const :as const]
   [ring.util.codec :as codec]))

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

(defn ->base-coll
  [data]
  (cond (nil? data) []
        (empty? data) []
        :else data))

(defn ->coll
  [data]
  (let [coll (->base-coll data)]
    (if (string? coll)
      [coll]
      coll)))

(defn split-comma->coll
  [data]
  (let [coll (->base-coll data)]
    (if (string? coll)
      (string/split data #",")
      coll)))

(defn split-comma->sorted-coll
  [data]
  (sort (split-comma->coll data)))

(defn seq->str
  [data]
  (string/join "," data))

(defn temporal-seq->cmr-query
  [data]
  (let [sep (str (codec/url-encode "temporal[]") "=")]
    (str sep
         (string/join (str "&" sep)
                      (map codec/url-encode data)))))

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

(defn bounding-box-lat
  [[_ lower-latitude _ upper-latitude]]
  [lower-latitude upper-latitude])

(defn bounding-box-lon
  [[lower-longitude _ upper-longitude _]]
  [lower-longitude upper-longitude])

(defn coverage->granules
  [coverage]
  (let [ids (filter #(string/starts-with? % "G") coverage)]
    (when (seq ids)
      ids)))

(defn coverage->collection
  [coverage]
  (let [id (filter #(string/starts-with? % "C") coverage)]
    (when (seq id)
      (first id))))

(defn not-array?
  [array]
  (or (nil? array)
      (empty? array)))

(defn unique-params-keys
  "This function returns only the record fields that are unique to the
  record of the given style. This is done by checking against a hard-coded set
  of fields shared that have been declared as common to all other parameter
  styles (see the `const` namespace)."
  ([record-constructor]
    (unique-params-keys record-constructor #{}))
  ([record-constructor other-allowed]
    (set/difference
     (set/union (set (keys (record-constructor {}))) other-allowed)
     const/shared-keys)))

(defn style?
  "This function checks the raw params to see if they have any keys that
  overlap with the WCS-style record."
  ([record-constructor raw-params]
    (style? record-constructor raw-params #{}))
  ([record-constructor raw-params other-allowed]
    (seq (set/intersection
          (set (keys raw-params))
          (unique-params-keys record-constructor other-allowed)))))

(defn ambiguous-style?
  [raw-params]
  (set/subset? (set (keys raw-params)) const/shared-keys))

(defn get-array-param
  "This pulls parameters names like `param[]` out of the params
  and extracts their values as a collection."
  [params param-key]
  (let [str-key (str (name param-key) "[]")]
    (case param-key
      :temporal (->coll
                  (or (get params str-key)
                      (get params (keyword str-key))))
      (split-comma->coll
        (or (get params str-key)
            (get params (keyword str-key)))))))

(defn empty-param?
  "Returns true if parameter is nil/empty/blank."
  [param]
  (cond
    (nil? param) true
    (coll? param) (empty? param)
    (string? param) (string/blank? param)
    :else false))

(defn update-values
  "Apply function to map based on list of keys."
  [m keys f]
  (reduce #(update-in %1 [%2] f) m keys))

(defn adjust-query-string
  "Special case query string adjustments."
  [params]
  (update-values params [:bounding-box :bbox] seq->str))

(defn remove-keys-if
  "Remove keys predicated on values."
  [m f]
  (apply dissoc m (filter #(f (% m)) (keys m))))

(defn ->query-string
  "Convert to query string."
  [params]
  (-> params
      (adjust-query-string)
      (remove-keys-if empty-param?)
      (codec/form-encode)))
