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
   [:RestrictionFlag (xpath "/AccessConstraints/Value")]
   [:RestrictionComment (xpath "/AccessConstraints/Description")]

   ;; We're assuming there is only one TemporalExtent for now. Issue CMR-1933 has been opened to
   ;; address questions about temporal mappings.
   (for-each "/TemporalExtents[1]"
     [:Temporal
      [:TemporalRangeType (xpath "TemporalRangeType")]
      [:PrecisionOfSeconds (xpath "PrecisionOfSeconds")]
      [:EndsAtPresentFlag (xpath "EndsAtPresentFlag")]
      (for-each "RangeDateTimes"
        [:RangeDateTime
         [:BeginningDateTime (xpath "BeginningDateTime")]
         [:EndingDateTime (xpath "EndingDateTime")]])

      (for-each "SingleDateTimes" [:SingleDateTime (xpath ".")])

      (for-each "PeriodicDateTimes"
        [:PeriodicDateTime
         [:Name (xpath "Name")]
         [:StartDate (xpath "StartDate")]
         [:EndDate (xpath "EndDate")]
         [:DurationUnit (xpath "DurationUnit")]
         [:DurationValue (xpath "DurationValue")]
         [:PeriodCycleDurationUnit (xpath "PeriodCycleDurationUnit")]
         [:PeriodCycleDurationValue (xpath "PeriodCycleDurationValue")]])])
   ])

