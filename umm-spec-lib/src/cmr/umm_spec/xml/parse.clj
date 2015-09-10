(ns cmr.umm-spec.xml.parse
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.common.date-time-parser :as dtp]))

(defn value-of
  [element xpath]
  (text (select element xpath)))

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
