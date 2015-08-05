(ns cmr.umm-spec.xml-mappings.echo10
  "Defines mappings from a UMM record into ECHO10 XML"
  (:require [cmr.umm-spec.xml-mappings.dsl :refer :all]))

(def umm-c-to-echo10-xml
  [:Collection
   [:ShortName (xpath "/UMM-C/EntryId/Id")]
   [:DataSetId (xpath "/UMM-C/EntryTitle")]
   [:Description (xpath "/UMM-C/Abstract")]

   (for-each "/UMM-C/TemporalExtent[1]"
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

