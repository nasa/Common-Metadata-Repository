(ns cmr.umm-spec.xml.parse
  (:require [clojure.string :as str]
            [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.common.date-time-parser :as dtp]))

(defn value-of
  [element xpath]
  (let [value (text (select element xpath))]
    (when-not (str/blank? value)
      value)))

(defn values-at
  "Returns seq of contents of elements at xpath."
  [element xpath]
  (map text (select element xpath)))

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
