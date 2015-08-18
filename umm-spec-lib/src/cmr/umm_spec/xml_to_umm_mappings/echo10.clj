(ns cmr.umm-spec.xml-to-umm-mappings.echo10
  "Defines mappings from ECHO10 XML into UMM records"
  (:require [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

(def temporal-mappings
  (for-each "/Collection/Temporal"
    (object
      {:TemporalRangeType (xpath "TemporalRangeType")
       :PrecisionOfSeconds (xpath "PrecisionOfSeconds")
       :EndsAtPresentFlag (xpath "EndsAtPresentFlag")
       :RangeDateTime (for-each "RangeDateTime"
                        (object
                          {:BeginningDateTime (xpath "BeginningDateTime")
                           :EndingDateTime (xpath "EndingDateTime")}))
       :SingleDateTime (select "SingleDateTime")
       :PeriodicDateTime (for-each "PeriodicDateTime"
                           (object
                             {:Name (xpath "Name")
                              :StartDate (xpath "StartDate")
                              :EndDate (xpath "EndDate")
                              :DurationUnit (xpath "DurationUnit")
                              :DurationValue (xpath "DurationValue")
                              :PeriodCycleDurationUnit (xpath "PeriodCycleDurationUnit")
                              :PeriodCycleDurationValue (xpath "PeriodCycleDurationValue")}))})))

(def echo10-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object
      {:EntryTitle (xpath "/Collection/DataSetId")
       :EntryId (object
                  {:Id (concat-parts
                         (xpath "/Collection/ShortName")
                         (constant  "_")
                         (xpath "/Collection/VersionId"))})
       :Abstract (xpath "/Collection/Description")
       :Purpose (xpath "/Collection/SuggestedUsage")
       :TemporalExtent temporal-mappings})))
