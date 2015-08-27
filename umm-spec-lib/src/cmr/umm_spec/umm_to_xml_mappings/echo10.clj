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

   ;; We're assuming there is only one TemporalExtent for now. Issue CMR-1933 has been opened to
   ;; address questions about temporal mappings.
   (for-each "/TemporalExtents[1]"
     [:Temporal
      :TemporalRangeType
      :PrecisionOfSeconds
      :EndsAtPresentFlag

      (for-each "RangeDateTimes"
        [:RangeDateTime
         :BeginningDateTime
         :EndingDateTime])

      (for-each "SingleDateTimes" [:SingleDateTime (xpath ".")])

      (for-each "PeriodicDateTimes"
        [:PeriodicDateTime
         :Name
         :StartDate
         :EndDate
         :DurationUnit
         :DurationValue
         :PeriodCycleDurationUnit
         :PeriodCycleDurationValue])])

   [:Platforms
    (for-each "/Platforms"
      [:Platform
       :ShortName
       :LongName
       :Type
       [:Characteristics
        (for-each "Characteristics"
          [:Characteristic
           :Name
           :Description
           :DataType
           :Unit
           :Value])]])]])
