(ns cmr.umm.echo10.collection
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.xml-schema-validator :as v]))


(defn- xml-elem->RangeDateTime
  "Returns a UMM RangeDateTime from a parsed Temporal XML structure"
  [temporal-content]
  (if-let [content (cx/content-at-path temporal-content [:RangeDateTime])]
    (c/map->RangeDateTime {:beginning-date-time (cx/datetime-at-path content [:BeginningDateTime])
                           :ending-date-time (cx/datetime-at-path content [:EndingDateTime])})))

(defn- xml-elem->PeriodicDateTime
  "Returns a UMM PeriodicDateTime from a parsed Temporal XML structure"
  [temporal-content]
  (if-let [content (cx/content-at-path temporal-content [:PeriodicDateTime])]
    (c/map->PeriodicDateTime {:name (cx/string-at-path content [:Name])
                              :start-date (cx/datetime-at-path content [:StartDate])
                              :end-date (cx/datetime-at-path content [:EndDate])
                              :duration-unit (cx/string-at-path content [:DurationUnit])
                              :duration-value (cx/long-at-path content [:DurationValue])
                              :period-cycle-duration-unit (cx/string-at-path content [:PeriodCycleDurationUnit])
                              :period-cycle-duration-value (cx/long-at-path content [:PeriodCycleDurationValue])})))

(defn- xml-elem->TemporalCoverage
  "Returns a UMM TemporalCoverage from a parsed Collection Content XML structure"
  [collection-content]
  (let [content (cx/content-at-path collection-content [:Temporal])
        range-date-time (xml-elem->RangeDateTime content)
        periodic-date-time (xml-elem->PeriodicDateTime content)
        temp-map {:time-type (cx/string-at-path content [:TimeType])
                  :date-type (cx/string-at-path content [:DateType])
                  :temporal-range-type (cx/string-at-path content [:TemporalRangeType])
                  :precision-of-seconds (cx/long-at-path content [:PrecisionOfSeconds])
                  :ends-at-present-flag (cx/bool-at-path content [:EndsAtPresentFlag])
                  :range-date-time range-date-time
                  :single-date-time (cx/datetime-at-path content [:SingleDateTime])
                  :periodic-date-time periodic-date-time}
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
  (let [collection-content (cx/content-at-path xml-struct [:Collection])
        product (xml-elem->Product collection-content)
        temporal-coverage (xml-elem->TemporalCoverage collection-content)]
    (c/map->UmmCollection {:entry-id (str (:short-name product) "_" (:version-id product))
                           :entry-title (cx/string-at-path collection-content [:DataSetId])
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
                ends-at-present-flag range-date-time single-date-time periodic-date-time]} temporal
        {:keys [beginning-date-time ending-date-time]} range-date-time
        {:keys [name start-date end-date duration-unit duration-value period-cycle-duration-unit period-cycle-duration-value]} periodic-date-time]
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
                            (if (some #(not (nil? %)) [beginning-date-time ending-date-time])
                              (x/element :RangeDateTime {}
                                         (if (not (nil? beginning-date-time)) (x/element :BeginningDateTime {} (str beginning-date-time)))
                                         (if (not (nil? ending-date-time)) (x/element :EndingDateTime {} (str ending-date-time)))))
                            (if (not (nil? single-date-time)) (x/element :SingleDateTime {} (str single-date-time)))
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
                                         (x-element :PeriodCycleDurationValue period-cycle-duration-value))))))))

(defn validate-xml
  "Validates the XML against the ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Collection.xsd") xml))
