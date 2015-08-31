(ns cmr.umm-spec.umm-to-xml-mappings.echo10
  "Defines mappings from a UMM record into ECHO10 XML"
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

(def umm-c-to-echo10-xml
  [:Collection
   [:ShortName (xpath "/EntryId")]
   [:VersionId (xpath "/Version")]
   [:InsertTime "1999-12-31T19:00:00-05:00"]
   [:LastUpdate "1999-12-31T19:00:00-05:00"]
   [:LongName "dummy-long-name"]
   [:DataSetId (xpath "/EntryTitle")]
   [:Description (xpath "/Abstract")]
   [:Orderable "true"]
   [:Visible "true"]
   [:SuggestedUsage (xpath "/Purpose")]
   [:TemporalKeywords
    (for-each "/TemporalKeywords"
              [:Keyword (xpath ".")])]

   ;; We're assuming there is only one TemporalExtent for now. Issue CMR-1933 has been opened to
   ;; address questions about temporal mappings.
   (for-each "/TemporalExtents[1]"
     (matching-object :Temporal
                      :Temporal
                      :TemporalRangeType
                      :PrecisionOfSeconds
                      :EndsAtPresentFlag

                      (for-each "RangeDateTimes"
                        (matching-object :RangeDateTime
                                         :BeginningDateTime
                                         :EndingDateTime))

                      (for-each "SingleDateTimes" [:SingleDateTime (xpath ".")])

                      (for-each "PeriodicDateTimes"
                        (matching-object :PeriodicDateTime
                                         :Name
                                         :StartDate
                                         :EndDate
                                         :DurationUnit
                                         :DurationValue
                                         :PeriodCycleDurationUnit
                                         :PeriodCycleDurationValue))))

   [:Platforms
    (for-each "/Platforms"
      (matching-object :Platform
                       :ShortName
                       :LongName
                       :Type
                       [:Characteristics
                        (for-each "Characteristics"
                          (matching-object :Characteristic
                                           :Name
                                           :Description
                                           :DataType
                                           :Unit
                                           :Value))]))]])
