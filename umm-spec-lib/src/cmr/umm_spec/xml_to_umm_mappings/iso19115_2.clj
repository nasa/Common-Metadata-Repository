(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.json-schema :as js]))

(def md-data-id-base-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification")

(def citation-base-xpath
  (str md-data-id-base-xpath "/gmd:citation/gmd:CI_Citation"))

(def identifier-base-xpath
  (str citation-base-xpath "/gmd:identifier/gmd:MD_Identifier"))

(def constraints-xpath
  (str md-data-id-base-xpath "/gmd:resourceConstraints/gmd:MD_LegalConstraints"))

(def temporal-xpath
  "Temoral xpath relative to md-data-id-base-xpath"
  (str "gmd:extent/gmd:EX_Extent/gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent"))

(def precision-xpath (str "/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality/gmd:report"
                          "/gmd:DQ_AccuracyOfATimeMeasurement/gmd:result"
                          "/gmd:DQ_QuantitativeResult/gmd:value"
                          "/gco:Record[@xsi:type='gco:Real_PropertyType']/gco:Real"))

(def platforms-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:platform"
       "/eos:EOS_Platform"))

(def projects-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:operation"
       "/gmi:MI_Operation"))

(def long-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:description/gco:CharacterString")

(def short-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")

(def characteristics-xpath
  "eos:otherProperty/gco:Record/eos:AdditionalAttributes/eos:AdditionalAttribute")

(def pc-attr-base-path
  "eos:reference/eos:EOS_AdditionalAttributeDescription")

(def distributor-xpath
  "/gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution/gmd:distributor/gmd:MD_Distributor")

(def distributor-fees-xpath
  (str distributor-xpath
       "/gmd:distributionOrderProcess/gmd:MD_StandardOrderProcess/gmd:fees/gco:CharacterString"))

(def distributor-format-xpath
  (str distributor-xpath "/gmd:distributorFormat/gmd:MD_Format/gmd:name/gco:CharacterString"))

(def distributor-media-xpath
  (str distributor-xpath
       "/gmd:distributorFormat/gmd:MD_Format/gmd:specification/gco:CharacterString"))

(def distributor-size-xpath
  (str distributor-xpath
       "/gmd:distributorTransferOptions/gmd:MD_DigitalTransferOptions/gmd:transferSize/gco:Real"))

(defn- char-string-value
  "Utitlity function to return the gco:CharacterString element value of the given parent xpath."
  [element parent-xpath]
  (value-of element (str parent-xpath "/gco:CharacterString")))

(defn- descriptive-keywords
  "Returns the descriptive keywords values for the given parent element and keyword type"
  [md-data-id-el keyword-type]
  (values-at md-data-id-el
             (str "gmd:descriptiveKeywords/gmd:MD_Keywords"
                  (format "[gmd:type/gmd:MD_KeywordTypeCode/@codeListValue='%s']" keyword-type)
                  "/gmd:keyword/gco:CharacterString")))

(defn- regex-value
  "Utitlity function to return the value of the element that matches the given xpath and regex."
  [element xpath regex]
  (when-let [elements (select element xpath)]
    (first (for [match-el elements
                 :let [match (re-matches regex (text match-el))]
                 :when match]
             ;; A string response implies there is no group in the regular expression and the
             ;; entire matching string is returned and if there is a group in the regular
             ;; expression, the first group of the matching string is returned.
             (if (string? match) match (second match))))))

(defn- parse-characteristics
  "Returns the parsed platform characteristics from the platform element."
  [element]
  (for [chars (select element characteristics-xpath)]
    {:Name        (char-string-value chars (str pc-attr-base-path "/eos:name"))
     :Description (char-string-value chars (str pc-attr-base-path "/eos:description"))
     :DataType    (value-of chars (str pc-attr-base-path "/eos:dataType/eos:EOS_AdditionalAttributeDataTypeCode"))
     :Unit        (char-string-value chars (str pc-attr-base-path "/eos:parameterUnitsOfMeasure"))
     :Value       (char-string-value chars (str "eos:value"))}))

(defn- parse-instrument-sensors
  "Returns the parsed instrument sensors from the instrument element."
  [instrument]
  (for [sensor (select instrument "eos:sensor/eos:EOS_Sensor")]
    {:ShortName (char-string-value sensor "eos:identifier/gmd:MD_Identifier/gmd:code")
     :LongName (char-string-value sensor "eos:identifier/gmd:MD_Identifier/gmd:description")
     :Technique (char-string-value sensor "eos:type")
     :Characteristics (parse-characteristics sensor)}))

(defn- parse-platform-instruments
  "Returns the parsed platform instruments from the platform element."
  [platform]
  (for [instrument (select platform "gmi:instrument/eos:EOS_Instrument")]
    {:ShortName (value-of instrument short-name-xpath)
     :LongName (value-of instrument long-name-xpath)
     :Technique (char-string-value instrument "gmi:type")
     :Characteristics (parse-characteristics instrument)
     :Sensors (parse-instrument-sensors instrument)}))

(defn- parse-platforms
  "Returns the platforms parsed from the given xml document."
  [doc]
  (for [platform (select doc platforms-xpath)]
    {:ShortName (value-of platform short-name-xpath)
     :LongName (value-of platform long-name-xpath)
     :Type (char-string-value platform "gmi:description")
     :Characteristics (parse-characteristics platform)
     :Instruments (parse-platform-instruments platform)}))

(defn- parse-projects
  "Returns the projects parsed from the given xml document."
  [doc]
  (for [proj (select doc projects-xpath)]
    (let [short-name (value-of proj short-name-xpath)
          description (char-string-value proj "gmi:description")
          ;; ISO description is built as "short-name > long-name", so here we extract the long-name out
          long-name (when-not (= short-name description)
                      (str/replace description (str short-name " > ") ""))]
      {:ShortName short-name
       :LongName long-name})))

(defn- parse-distributions
  "Returns the distributions parsed from the given xml document."
  [doc]
  (let [medias (values-at doc distributor-media-xpath)
        sizes (values-at doc distributor-size-xpath)
        formats (values-at doc distributor-format-xpath)
        fees (values-at doc distributor-fees-xpath)]
    (util/map-longest (fn [media size format fee]
                        (hash-map
                          :DistributionMedia media
                          :DistributionSize size
                          :DistributionFormat format
                          :Fees fee))
                      ""
                      medias sizes formats fees)))

(defn- parse-iso19115-xml
  "Returns UMM-C collection structure from ISO19115-2 collection XML document."
  [doc]
  (let [md-data-id-el (first (select doc md-data-id-base-xpath))
        citation-el (first (select doc citation-base-xpath))
        id-el (first (select doc identifier-base-xpath))]
    {:EntryId (char-string-value id-el "gmd:code")
     :EntryTitle (char-string-value citation-el "gmd:title")
     :Version (char-string-value id-el "gmd:version")
     :Abstract (char-string-value md-data-id-el "gmd:abstract")
     :Purpose (char-string-value md-data-id-el "gmd:purpose")
     :CollectionProgress (value-of md-data-id-el "gmd:status/gmd:MD_ProgressCode")
     ;; TODO: Fix AccessConstraints. Access Constraints should likely be treated as an array
     ;; in the JSON schema instead of a single object. CMR-1989.
     :AccessConstraints {:Description
                         (regex-value doc (str constraints-xpath
                                               "/gmd:useLimitation/gco:CharacterString")
                                      #"Restriction Comment:(.+)")

                         :Value
                         (regex-value doc (str constraints-xpath
                                               "/gmd:otherConstraints/gco:CharacterString")
                                      #"Restriction Flag:(.+)")}
     ;; TODO: Fix UseConstraints. Use Constraints should likely be treated as an array
     ;; in the JSON schema instead of a single string. CMR-1989.
     :UseConstraints
     (regex-value doc (str constraints-xpath "/gmd:useLimitation/gco:CharacterString")
                  #"^(?!Restriction Comment:).+")
     :SpatialKeywords (descriptive-keywords md-data-id-el "place")
     :TemporalKeywords (descriptive-keywords md-data-id-el "temporal")
     :DataLanguage (char-string-value md-data-id-el "gmd:language")
     :TemporalExtents (for [temporal (select md-data-id-el temporal-xpath)]
                        {:PrecisionOfSeconds (value-of doc precision-xpath)
                         :RangeDateTimes (for [period (select temporal "gml:TimePeriod")]
                                           {:BeginningDateTime (value-of period "gml:beginPosition")
                                            :EndingDateTime    (value-of period "gml:endPosition")})
                         :SingleDateTimes (values-at temporal "gml:TimeInstant/gml:timePosition")})
     :ProcessingLevel {:Id
                       (char-string-value
                         md-data-id-el
                         "gmd:processingLevel/gmd:MD_Identifier/gmd:code")

                       :ProcessingLevelDescription
                       (char-string-value
                         md-data-id-el
                         "gmd:processingLevel/gmd:MD_Identifier/gmd:description")}
     :Distributions (parse-distributions doc)
     :Platforms (parse-platforms doc)
     :Projects (parse-projects doc)}))

(defn iso19115-2-xml-to-umm-c
  "Returns UMM-C collection record from ISO19115-2 collection XML document."
  [metadata]
  (js/coerce (parse-iso19115-xml metadata)))


