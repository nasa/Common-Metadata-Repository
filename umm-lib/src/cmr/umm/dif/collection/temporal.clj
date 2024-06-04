(ns cmr.umm.dif.collection.temporal
  "Contains functions for parsing and generating the Temporal_Coverage element of DIF dialect."
  (:require [clojure.data.xml :as xml]
            [cmr.common.xml :as cx]
            [cmr.common.date-time-parser :as parser]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.dif.date-util :as date-util]))

(defn xml-elem->Temporal
  "Returns a list of UMM RangeDateTimes from a parsed DIF XML structure"
  [collection-element]
  (let [elements (cx/elements-at-path collection-element [:Temporal_Coverage])]
    (when-not (empty? elements)
      (let [range-date-times (map #(c/map->RangeDateTime
                                    {:beginning-date-time (parser/try-parse-datetime (cx/string-at-path % [:Start_Date]))
                                     :ending-date-time (date-util/parse-dif-end-date
                                                         (cx/string-at-path % [:Stop_Date]))})
                                  elements)]
        (c/map->Temporal {:range-date-times range-date-times
                          :single-date-times []
                          :periodic-date-times []})))))

(defn generate-temporal
  "Generates the Temporal_Coverage element of DIF XML from a UMM Collection temporal record."
  [temporal]
  (when temporal
    (for [range-date-time (:range-date-times temporal)]
      (let [{:keys [beginning-date-time ending-date-time]} range-date-time]
        (xml/element :Temporal_Coverage {}
                   (when beginning-date-time
                     (xml/element :Start_Date {} (str beginning-date-time)))
                   (when ending-date-time
                     (xml/element :Stop_Date {} (str ending-date-time))))))))
