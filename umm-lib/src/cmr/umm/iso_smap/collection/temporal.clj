(ns cmr.umm.iso-smap.collection.temporal
  "Contains functions for parsing and generating the ISO SMAP temporal"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.generator-util :as gu]))

(defn- xml-elem->RangeDateTimes
  "Returns a list of UMM RangeDateTimes from a parsed XML structure"
  [temporal-elem]
  (let [elements (cx/elements-at-path
                   temporal-elem
                   [:temporalElement :EX_TemporalExtent :extent :TimePeriod])]
    (map #(c/map->RangeDateTime {:beginning-date-time (cx/datetime-at-path % [:beginPosition])
                                 :ending-date-time (cx/datetime-at-path % [:endPosition])})
         elements)))

(defn xml-elem->Temporal
  "Returns a UMM Temporal from a parsed XML structure"
  [xml-struct]
  (let [temporal-elem (cx/element-at-path
                        xml-struct
                        [:seriesMetadata :MI_Metadata :identificationInfo :MD_DataIdentification
                         :extent :EX_Extent])
        single-date-times (cx/datetimes-at-path
                            temporal-elem
                            [:temporalElement :EX_TemporalExtent :extent :TimeInstant :timePosition])
        range-date-times (xml-elem->RangeDateTimes temporal-elem)]
    (when (or (seq single-date-times) (seq range-date-times))
      (c/map->Temporal {:range-date-times range-date-times
                        :single-date-times single-date-times
                        :periodic-date-times []}))))

(defn generate-temporal
  "Generates the temporal element from a UMM Collection temporal record."
  [temporal]
  (let [{:keys [range-date-times single-date-times]} temporal]
    (concat
      (for [range-date-time range-date-times]
        (let [{:keys [beginning-date-time ending-date-time]} range-date-time]
          (x/element
            :gmd:temporalElement {}
            (x/element
              :gmd:EX_TemporalExtent {}
              (x/element :gmd:extent {}
                         (x/element :gml:TimePeriod {:gml:id (gu/generate-id)}
                                    (when beginning-date-time
                                      (x/element :gml:beginPosition {} (str beginning-date-time)))
                                    (if ending-date-time
                                      (x/element :gml:endPosition {} (str ending-date-time))
                                      (x/element :gml:endPosition {}))))))))

      (for [single-date-time single-date-times]
        (x/element
          :gmd:temporalElement {}
          (x/element
            :gmd:EX_TemporalExtent {}
            (x/element :gmd:extent {}
                       (x/element :gml:TimeInstant {:gml:id (gu/generate-id)}
                                  (x/element :gml:timePosition {} (str single-date-time))))))))))

