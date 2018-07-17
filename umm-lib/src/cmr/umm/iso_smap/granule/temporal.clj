(ns cmr.umm.iso-smap.granule.temporal
  "Contains functions for parsing and generating the ISO SMAP granule temporal"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.umm-granule :as g]))

(defn- xml-elem->RangeDateTime
  "Returns a UMM RangeDateTime from a parsed XML structure"
  [temporal-elem]
  (let [elem (cx/element-at-path
               temporal-elem
               [:temporalElement :EX_TemporalExtent :extent :TimePeriod])
        beginning-date-time (cx/datetime-at-path elem [:beginPosition])
        ending-date-time (cx/datetime-at-path elem [:endPosition])]
    (when (or beginning-date-time ending-date-time)
      (c/map->RangeDateTime {:beginning-date-time beginning-date-time
                             :ending-date-time ending-date-time}))))

(defn xml-elem->Temporal
  "Returns a UMM GranuleTemporal from a parsed XML structure"
  [xml-struct]
  (let [temporal-elems (cx/elements-at-path
                        xml-struct
                        [:composedOf :DS_DataSet :has :MI_Metadata :identificationInfo
                         :MD_DataIdentification :extent :EX_Extent])]
    (first
     (for [elem temporal-elems
           :let [single-date-time (cx/datetime-at-path
                                   elem
                                   [:temporalElement :EX_TemporalExtent :extent :TimeInstant :timePosition])
                 range-date-time (xml-elem->RangeDateTime elem)]
           :when (or single-date-time range-date-time)]
       (g/map->GranuleTemporal {:range-date-time range-date-time
                                :single-date-time single-date-time})))))

(defn generate-temporal
  "Generates the temporal element from a UMM granule temporal record."
  [temporal]
  (let [{:keys [range-date-time single-date-time]} temporal]
    (if range-date-time
      (let [{:keys [beginning-date-time ending-date-time]} range-date-time]
        (x/element
          :gmd:temporalElement {}
          (x/element
            :gmd:EX_TemporalExtent {}
            (x/element :gmd:extent {}
                       (x/element :gml:TimePeriod {:gml:id "swathTemporalExtent"}
                                  (when beginning-date-time
                                    (x/element :gml:beginPosition {} (str beginning-date-time)))
                                  (if ending-date-time
                                    (x/element :gml:endPosition {} (str ending-date-time))
                                    (x/element :gml:endPosition {})))))))
      ;; single date time
      (when single-date-time
        (x/element
          :gmd:temporalElement {}
          (x/element
            :gmd:EX_TemporalExtent {}
            (x/element :gmd:extent {}
                       (x/element :gml:TimeInstant {:gml:id "swathTemporalExtent"}
                                  (x/element :gml:timePosition {} (str single-date-time))))))))))
