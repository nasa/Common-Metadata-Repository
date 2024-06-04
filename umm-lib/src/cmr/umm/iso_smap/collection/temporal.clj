(ns cmr.umm.iso-smap.collection.temporal
  "Contains functions for parsing and generating the ISO SMAP temporal"
  (:require [clojure.data.xml :as xml]
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
          (xml/element
            :gmd:temporalElement {}
            (xml/element
              :gmd:EX_TemporalExtent {}
              (xml/element :gmd:extent {}
                         (xml/element :gml:TimePeriod {:gml:id (gu/generate-id)}
                                    (when beginning-date-time
                                      (xml/element :gml:beginPosition {} (str beginning-date-time)))
                                    (if ending-date-time
                                      (xml/element :gml:endPosition {} (str ending-date-time))
                                      (xml/element :gml:endPosition {}))))))))

      (for [single-date-time single-date-times]
        (xml/element
          :gmd:temporalElement {}
          (xml/element
            :gmd:EX_TemporalExtent {}
            (xml/element :gmd:extent {}
                       (xml/element :gml:TimeInstant {:gml:id (gu/generate-id)}
                                  (xml/element :gml:timePosition {} (str single-date-time))))))))))

