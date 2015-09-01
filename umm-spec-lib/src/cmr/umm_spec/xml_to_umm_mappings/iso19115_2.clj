(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require clojure.string
            [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

;;; Path Utils

(def char-string "gco:CharacterString")

(def md-data-id-base-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification")

(def citation-base-xpath
  (str md-data-id-base-xpath "/gmd:citation/gmd:CI_Citation"))

(def identifier-base-xpath
  (str citation-base-xpath "/gmd:identifier/gmd:MD_Identifier"))

;;; Mapping

(def temporal-xpath
  (str md-data-id-base-xpath "/gmd:extent/gmd:EX_Extent/gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent"))

(def precision-xpath (str "/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality/gmd:report"
                          "/gmd:DQ_AccuracyOfATimeMeasurement/gmd:result"
                          "/gmd:DQ_QuantitativeResult/gmd:value"
                          "/gco:Record[@xsi:type='gco:Real_PropertyType']/gco:Real"))

(def temporal-keywords-xpath
  (select (str md-data-id-base-xpath
       "/gmd:descriptiveKeywords/gmd:MD_Keywords"
       "[gmd:type/gmd:MD_KeywordTypeCode/@codeListValue='temporal']"
       "/gmd:keyword/gco:CharacterString")))

(def temporal-mappings
  (for-each temporal-xpath
            (object {:PrecisionOfSeconds (xpath precision-xpath)
                     :RangeDateTimes (for-each "gml:TimePeriod"
                                               (object {:BeginningDateTime (xpath "gml:beginPosition")
                                                        :EndingDateTime    (xpath "gml:endPosition")}))
                     :SingleDateTimes (select "gml:TimeInstant/gml:timePosition")})))

(def platforms-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:platform"
       "/eos:EOS_Platform"))

(def platform-long-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:description/gco:CharacterString")

(def platform-short-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")

(def platform-characteristics-xpath
  "eos:otherProperty/gco:Record/eos:AdditionalAttributes/eos:AdditionalAttribute")

(def pc-attr-base-path
  "eos:reference/eos:EOS_AdditionalAttributeDescription")

(def constraints-xpath
  (str md-data-id-base-xpath "/gmd:resourceConstraints/gmd:MD_LegalConstraints"))

(def platform-characteristics-mapping
  (for-each platform-characteristics-xpath
    (object
     {:Name        (xpath pc-attr-base-path "eos:name" char-string)
      :Description (xpath pc-attr-base-path "eos:description" char-string)
      :DataType    (xpath pc-attr-base-path "eos:dataType/eos:EOS_AdditionalAttributeDataTypeCode")
      :Unit        (xpath pc-attr-base-path "eos:parameterUnitsOfMeasure" char-string)
      :Value       (xpath "eos:value" char-string)})))

(def iso19115-2-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object {:EntryId (char-string-xpath identifier-base-xpath "/gmd:code")
             :EntryTitle (char-string-xpath citation-base-xpath "/gmd:title")
             :Version (char-string-xpath identifier-base-xpath "/gmd:version")
             :Abstract (char-string-xpath md-data-id-base-xpath "/gmd:abstract")
             :Purpose (char-string-xpath md-data-id-base-xpath "/gmd:purpose")
             ;; TODO: Fix AccessConstraints. Access Constraints should likely be treated as an array
             ;; in the JSON schema instead of a single object. CMR-1989.
             :AccessConstraints (object
                                  {:Description
                                   (xpath-with-regex (str constraints-xpath
                                                          "/gmd:useLimitation/gco:CharacterString")
                                                     #"Restriction Comment:(.+)")

                                   :Value
                                   (xpath-with-regex (str constraints-xpath
                                                          "/gmd:otherConstraints/gco:CharacterString")
                                                     #"Restriction Flag:(.+)")})
             ;; TODO: Fix UseConstraints. Use Constraints should likely be treated as an array
             ;; in the JSON schema instead of a single string. CMR-1989.
             :UseConstraints
             (xpath-with-regex (str constraints-xpath "/gmd:useLimitation/gco:CharacterString")
                               #"^(?!Restriction Comment:).+")
             :TemporalKeywords temporal-keywords-xpath
             :DataLanguage (char-string-xpath md-data-id-base-xpath "/gmd:language")
             :TemporalExtents temporal-mappings
             :Platforms (for-each platforms-xpath
                          (object {:ShortName (xpath platform-short-name-xpath)
                                   :LongName (xpath platform-long-name-xpath)
                                   :Type (xpath "gmi:description/gco:CharacterString")
                                   :Characteristics platform-characteristics-mapping}))})))
