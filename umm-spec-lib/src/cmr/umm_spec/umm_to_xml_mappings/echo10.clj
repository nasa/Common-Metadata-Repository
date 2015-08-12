(ns cmr.umm-spec.umm-to-xml-mappings.echo10
  "Defines mappings from a UMM record into ECHO10 XML"
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

(def umm-c-to-echo10-xml
  [:Collection
   [:ShortName (xpath "/EntryId/Id")]
   [:DataSetId (xpath "/EntryTitle")]
   [:Description (xpath "/Abstract")]

   (for-each "/TemporalExtent[1]"
     [:Temporal
      [:TemporalRangeType (xpath "TemporalRangeType")]
      [:PrecisionOfSeconds (xpath "PrecisionOfSeconds")]
      [:EndsAtPresentFlag (xpath "EndsAtPresentFlag")]
      (for-each "RangeDateTime"
        [:RangeDateTime
         [:BeginningDateTime (xpath "BeginningDateTime")]
         [:EndingDateTime (xpath "EndingDateTime")]])

      (for-each "SingleDateTime" [:SingleDateTime (xpath ".")])

      (for-each "PeriodicDateTime"
        [:PeriodicDateTime
         [:Name (xpath "Name")]
         [:StartDate (xpath "StartDate")]
         [:EndDate (xpath "EndDate")]
         [:DurationUnit (xpath "DurationUnit")]
         [:DurationValue (xpath "DurationValue")]
         [:PeriodCycleDurationUnit (xpath "PeriodCycleDurationUnit")]
         [:PeriodCycleDurationValue (xpath "PeriodCycleDurationValue")]])])])

