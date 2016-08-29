(ns cmr.common.xml.parse
  (:require [clojure.string :as str]
            [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.date-time-parser :as dtp]))

(defn- blank-to-nil
  "Returns the given string or nil if it is blank."
  [s]
  (when-not (str/blank? s)
    s))

(defn value-of
  [element xpath]
  (let [value (text (select element xpath))]
    (blank-to-nil value)))

(defn values-at
  "Returns seq of contents of elements at xpath."
  [element xpath]
  (let [values (map text (select element xpath))]
    (map blank-to-nil values)))

(defn boolean-at
  [element xpath]
  (= "true" (value-of element xpath)))

(defn fields-from
  [element & kws]
  (zipmap kws
          (for [kw kws]
            (value-of element (name kw)))))

(defn dates-at
  [element xpath]
  (map #(dtp/parse-datetime (text %)) (select element xpath)))

(defn date-at
  [element xpath]
  (when-let [value (text (value-of element xpath))]
    (dtp/parse-datetime value)))
