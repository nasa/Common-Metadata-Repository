(ns cmr.umm.echo10.granule.temporal
  "Contains functions for parsing and generating the ECHO10 granule temporal element."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as c]
   [cmr.umm.umm-granule :as g]))

(defn- xml-elem->RangeDateTime
  "Returns a list of UMM RangeDateTimes from a parsed Temporal XML structure"
  [temporal-element]
  (when-let [element (cx/element-at-path temporal-element [:RangeDateTime])]
    (c/map->RangeDateTime
      {:beginning-date-time (cx/datetime-at-path element [:BeginningDateTime])
       :ending-date-time (cx/datetime-at-path element [:EndingDateTime])})))

(defn xml-elem->Temporal
  "Returns a UMM Temporal from a parsed Granule Content XML structure"
  [granule-element]
  (when-let [temporal-element (cx/element-at-path granule-element [:Temporal])]
    (let [range-date-time (xml-elem->RangeDateTime temporal-element)]
      (g/map->GranuleTemporal
        {:range-date-time range-date-time
         :single-date-time (cx/datetime-at-path temporal-element [:SingleDateTime])}))))

(defn generate-temporal
  "Generates the temporal element of ECHO10 XML from a UMM Granule temporal record."
  [temporal]
  (when temporal
    (let [{:keys [range-date-time single-date-time]} temporal]
      (xml/element :Temporal {}
                 (when range-date-time
                   (let [{:keys [beginning-date-time ending-date-time]} range-date-time]
                     (xml/element :RangeDateTime {}
                                (when beginning-date-time
                                  (xml/element :BeginningDateTime {} (str beginning-date-time)))
                                (when ending-date-time
                                  (xml/element :EndingDateTime {} (str ending-date-time))))))

                 (when single-date-time
                   (xml/element :SingleDateTime {} (str single-date-time)))))))

