(ns cmr.umm-spec.xml-to-umm-mappings.echo10
  "Defines mappings from ECHO10 XML into UMM records"
  (:require [cmr.common.xml :as cx]
            [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

(def temporal-mappings
  (for-each "/Collection/Temporal"
    (object
      {:TemporalRangeType (xpath "TemporalRangeType")
       :PrecisionOfSeconds (xpath "PrecisionOfSeconds")
       :EndsAtPresentFlag (xpath "EndsAtPresentFlag")
       :RangeDateTimes (for-each "RangeDateTime"
                         (matching-object :BeginningDateTime :EndingDateTime))
       :SingleDateTimes (select "SingleDateTime")
       :PeriodicDateTimes (for-each "PeriodicDateTime"
                            (matching-object :Name :StartDate :EndDate :DurationUnit :DurationValue
                                             :PeriodCycleDurationUnit :PeriodCycleDurationValue))})))

(def characteristic-mapping
  (matching-object :Name
                   :Description
                   :DataType
                   :Unit
                   :Value))

(def sensor-mapping
  (object {:ShortName (xpath "ShortName")
           :LongName (xpath "LongName")
           :Technique (xpath "Technique")
           :Characteristics (for-each "Characteristics/Characteristic"
                              characteristic-mapping)}))

(def instrument-mapping
  (object {:ShortName (xpath "ShortName")
           :LongName (xpath "LongName")
           :Technique (xpath "Technique")
           :NumberOfSensors (xpath "NumberOfSensors")
           :OperationalModes (select "OperationModes/OperationMode")
           :Characteristics (for-each "Characteristics/Characteristic"
                              characteristic-mapping)
           :Sensors (for-each "Sensors/Sensor"
                      sensor-mapping)}))

(def echo10-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object
      {:EntryTitle (xpath "/Collection/DataSetId")
       :EntryId (xpath "/Collection/ShortName")
       :Version (xpath "/Collection/VersionId")
       :Abstract (xpath "/Collection/Description")
       :CollectionDataType (xpath "/Collection/CollectionDataType")
       :Purpose (xpath "/Collection/SuggestedUsage")
       :CollectionProgress (xpath "/Collection/CollectionState")
       :AccessConstraints (object
                            {:Description (xpath "/Collection/RestrictionComment")
                             :Value (xpath "/Collection/RestrictionFlag")})
       :Distributions (for-each "/Collection"
                                (object
                                  {:DistributionFormat (xpath "DataFormat")
                                   :Fees (xpath "Price")}))
       :TemporalKeywords (select "/Collection/TemporalKeywords/Keyword")
       :TemporalExtents temporal-mappings
       :Platforms (for-each "/Collection/Platforms/Platform"
                            (object {:ShortName (xpath "ShortName")
                                     :LongName (xpath "LongName")
                                     :Type (xpath "Type")
                                     :Characteristics (for-each "Characteristics/Characteristic"
                                                                characteristic-mapping)
                                     :Instruments (for-each "Instruments/Instrument"
                                                            instrument-mapping)}))
       :ProcessingLevel (object
                          {:Id (xpath "/Collection/ProcessingLevelId")
                           :ProcessingLevelDescription (xpath "/Collection/ProcessingLevelDescription")})
       :AdditionalAttributes (for-each "/Collection/AdditionalAttributes/AdditionalAttribute"
                                       (matching-object :Name :Description :DataType :ParameterRangeBegin
                                                        :ParameterRangeEnd :Value))})))
