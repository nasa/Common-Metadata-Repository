(ns cmr.umm-spec.umm-to-xml-mappings.new-echo10
  "Defines mappings from a UMM record into ECHO10 XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.simple-xpath :refer [select value-at]]))

(defn characteristic-mapping
  [data]
  [:Characteristic
   (elements-from data
                  :Name
                  :Description
                  :DataType
                  :Unit
                  :Value)])

(defn echo10-xml
  "Returns ECHO10 XML structure from UMM collection record c."
  [c]
  (xml
   [:Collection
    [:ShortName (value-at c "/EntryId")]
    [:VersionId (value-at c "/Version")]
    [:InsertTime "1999-12-31T19:00:00-05:00"]
    [:LastUpdate "1999-12-31T19:00:00-05:00"]
    [:LongName "dummy-long-name"]
    [:DataSetId (value-at c "/EntryTitle")]
    [:Description (value-at c "/Abstract")]
    [:CollectionDataType (value-at c "/CollectionDataType")]
    [:Orderable "true"]
    [:Visible "true"]
    [:SuggestedUsage (value-at c "/Purpose")]
    [:ProcessingLevelId (value-at c "/ProcessingLevel/Id")]
    [:ProcessingLevelDescription (value-at c "/ProcessingLevel/ProcessingLevelDescription")]
    [:CollectionState (value-at c "/CollectionProgress")]
    [:RestrictionFlag (value-at c "/AccessConstraints/Value")]
    [:RestrictionComment (value-at c "/AccessConstraints/Description")]
    [:TemporalKeywords
     (for [kw (select c "/TemporalKeywords")]
       [:Keyword kw])]
    ;; We're assuming there is only one TemporalExtent for now. Issue CMR-1933 has been opened to
    ;; address questions about temporal mappings.
    (when-let [temporal (value-at c "/TemporalExtents")]
      [:Temporal
       (elements-from temporal
                      :TemporalRangeType
                      :PrecisionOfSeconds
                      :EndsAtPresentFlag)

       (for [r (select temporal "RangeDateTimes")]
         [:RangeDateTime (elements-from r :BeginningDateTime :EndingDateTime)])
       
       (for [date (select temporal "SingleDateTimes")]
         [:SingleDateTime (str date)])

       (for [pdt (select temporal "PeriodicDateTimes")]
         [:PeriodicDateTime
          (elements-from pdt
                         :Name
                         :StartDate
                         :EndDate
                         :DurationUnit
                         :DurationValue
                         :PeriodCycleDurationUnit
                         :PeriodCycleDurationValue)])])

    [:Platforms
     (for [plat (select c "/Platforms")]
       [:Platform
        (elements-from plat
                       :ShortName
                       :LongName
                       :Type)
        [:Characteristics
         (for [cc (select plat "Characteristics")]
           (characteristic-mapping cc))]
        [:Instruments
         (for [inst (select plat "Instruments")]
           [:Instrument
            (elements-from inst
                           :ShortName
                           :LongName
                           :Technique
                           :NumberOfSensors)
            [:Characteristics
             (for [cc (select inst "Characteristics")]
               (characteristic-mapping cc))]
            [:Sensors
             (for [ss (select inst "Sensors")]
               [:Sensor
                (elements-from ss
                               :ShortName
                               :LongName
                               :Technique)
                [:Characteristics
                 (map characteristic-mapping (select ss "Characteristics"))]])]
            [:OperationModes
             (for [mode (select inst "OperationalModes")]
               [:OperationMode mode])]])]])]
    [:AdditionalAttributes
     (for [aa (select c "/AdditionalAttributes")]
       [:AdditionalAttribute
        (elements-from aa
                       :Name :Description :DataType :ParameterRangeBegin
                       :ParameterRangeEnd :Value)])]]))
