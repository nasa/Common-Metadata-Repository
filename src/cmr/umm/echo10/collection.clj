(ns cmr.umm.echo10.collection
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.xml-schema-validator :as v]))

;; TODO update temporal method names and documentation to indicate they are not singular

(defn- xml-elem->RangeDateTimes
  "Returns a UMM RangeDateTime from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:RangeDateTime])]
    (map #(c/map->RangeDateTime {:beginning-date-time (cx/datetime-at-path % [:BeginningDateTime])
                                 :ending-date-time (cx/datetime-at-path % [:EndingDateTime])})
         elements)))

(defn- xml-elem->PeriodicDateTime
  "Returns a UMM PeriodicDateTime from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:PeriodicDateTime])]
    (map #(c/map->PeriodicDateTime {:name (cx/string-at-path % [:Name])
                                    :start-date (cx/datetime-at-path % [:StartDate])
                                    :end-date (cx/datetime-at-path % [:EndDate])
                                    :duration-unit (cx/string-at-path % [:DurationUnit])
                                    :duration-value (cx/long-at-path % [:DurationValue])
                                    :period-cycle-duration-unit (cx/string-at-path % [:PeriodCycleDurationUnit])
                                    :period-cycle-duration-value (cx/long-at-path % [:PeriodCycleDurationValue])})
         elements)))

(defn- xml-elem->TemporalCoverage
  "Returns a UMM TemporalCoverage from a parsed Collection Content XML structure"
  [collection-element]
  (let [temporal-element (cx/element-at-path collection-element [:Temporal])
        range-date-times (xml-elem->RangeDateTimes temporal-element)
        periodic-date-times (xml-elem->PeriodicDateTime temporal-element)
        temp-map {:time-type (cx/string-at-path temporal-element [:TimeType])
                  :date-type (cx/string-at-path temporal-element [:DateType])
                  :temporal-range-type (cx/string-at-path temporal-element [:TemporalRangeType])
                  :precision-of-seconds (cx/long-at-path temporal-element [:PrecisionOfSeconds])
                  :ends-at-present-flag (cx/bool-at-path temporal-element [:EndsAtPresentFlag])
                  :range-date-times range-date-times
                  :single-date-times (cx/datetimes-at-path temporal-element [:SingleDateTime])
                  :periodic-date-times periodic-date-times}
        simplified-map (apply dissoc temp-map (for [[k v] temp-map :when (nil? v)] k))]
    (c/map->TemporalCoverage simplified-map)))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (c/map->Product {:short-name (cx/string-at-path collection-content [:ShortName])
                   :long-name (cx/string-at-path collection-content [:LongName])
                   :version-id (cx/string-at-path collection-content [:VersionId])}))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [product (xml-elem->Product xml-struct)
        temporal-coverage (xml-elem->TemporalCoverage xml-struct)]
    (c/map->UmmCollection {:entry-id (str (:short-name product) "_" (:version-id product))
                           :entry-title (cx/string-at-path xml-struct [:DataSetId])
                           :product product
                           :temporal-coverage temporal-coverage})))

(defn- x-element
  "Returns the xml element if value is not null"
  [element-name value]
  (if-not (nil? value) (x/element element-name {} value)))

(defn parse-collection
  "Parses ECHO10 XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(defn generate-collection
  "Generates ECHO10 XML from a UMM Collection record."
  [collection]

  (let [{{:keys [short-name long-name version-id]} :product
         dataset-id :entry-title
         temporal :temporal-coverage} collection
        {:keys [time-type date-type temporal-range-type precision-of-seconds
                ends-at-present-flag range-date-times single-date-times periodic-date-times]} temporal]
    (x/emit-str
      (x/element :Collection {}
                 (x/element :ShortName {} short-name)
                 (x/element :VersionId {} version-id)
                 ;; required fields that are not implemented yet are stubbed out.
                 (x/element :InsertTime {} "1999-12-31T19:00:00Z")
                 (x/element :LastUpdate {} "1999-12-31T19:00:00Z")
                 (x/element :LongName {} long-name)
                 (x/element :DataSetId {} dataset-id)
                 (x/element :Description {} "stubbed")
                 (x/element :Orderable {} "true")
                 (x/element :Visible {} "true")
                 (x/element :Temporal {}
                            (x-element :TimeType time-type)
                            (x-element :DateType date-type)
                            (x-element :TemporalRangeType temporal-range-type)
                            (x-element :PrecisionOfSeconds precision-of-seconds)
                            (x-element :EndsAtPresentFlag ends-at-present-flag)

                            (for [range-date-time range-date-times]
                              (let [{:keys [beginning-date-time ending-date-time]} range-date-time]
                                (when (some #(not (nil? %)) [beginning-date-time ending-date-time])
                                  (x/element :RangeDateTime {}
                                             (when (not (nil? beginning-date-time))
                                               (x/element :BeginningDateTime {} (str beginning-date-time)))
                                             (when (not (nil? ending-date-time))
                                               (x/element :EndingDateTime {} (str ending-date-time)))))))

                            (for [single-date-time single-date-times]
                              (x/element :SingleDateTime {} (str single-date-time)))

                            (for [periodic-date-time periodic-date-times]
                              (let [{:keys [name start-date end-date duration-unit duration-value
                                            period-cycle-duration-unit period-cycle-duration-value]} periodic-date-time]
                                (if (some #(not (nil? %)) [name
                                                           start-date
                                                           end-date
                                                           duration-unit
                                                           duration-value
                                                           period-cycle-duration-unit
                                                           period-cycle-duration-value])
                                  (x/element :PeriodicDateTime {}
                                             (x-element :Name name)
                                             (if (not (nil? start-date)) (x/element :StartDate {} (str start-date)))
                                             (if (not (nil? end-date)) (x/element :EndDate {} (str end-date)))
                                             (x-element :DurationUnit duration-unit)
                                             (x-element :DurationValue duration-value)
                                             (x-element :PeriodCycleDurationUnit period-cycle-duration-unit)
                                             (x-element :PeriodCycleDurationValue period-cycle-duration-value))))))))))

(defn validate-xml
  "Validates the XML against the ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Collection.xsd") xml))
