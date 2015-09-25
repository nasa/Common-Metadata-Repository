(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.spatial :as spatial]
            [clojure.data :as data]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.platform :as platform]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as dru]
            [cmr.umm-spec.iso19115-2-util :refer :all]))

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

(def projects-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:operation"
       "/gmi:MI_Operation"))

(def publication-xpath
  "Publication xpath relative to md-data-id-base-xpath"
  "gmd:aggregationInfo/gmd:MD_AggregateInformation/gmd:aggregateDataSetName/gmd:CI_Citation")

(defn- descriptive-keywords
  "Returns the descriptive keywords values for the given parent element and keyword type"
  [md-data-id-el keyword-type]
  (values-at md-data-id-el
             (str "gmd:descriptiveKeywords/gmd:MD_Keywords"
                  (format "[gmd:type/gmd:MD_KeywordTypeCode/@codeListValue='%s']" keyword-type)
                  "/gmd:keyword/gco:CharacterString")))

(defn- descriptive-keywords-type-not-equal
  "Returns the descriptive keyword values for the given parent element for all keyword types excepting
  those given"
  [md-data-id-el keyword-types-to-ignore]
  (let [keyword-types-to-ignore (set keyword-types-to-ignore)]
    (flatten
      (for [kw (select md-data-id-el "gmd:descriptiveKeywords/gmd:MD_Keywords")
            :when (not (keyword-types-to-ignore (value-of kw "gmd:type/gmd:MD_KeywordTypeCode")))]
        (values-at kw "gmd:keyword/gco:CharacterString")))))

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
     :SpatialExtent (spatial/parse-spatial doc)
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
     :Distributions (dru/parse-distributions doc)
     :Platforms (platform/parse-platforms doc)
     :Projects (parse-projects doc)

     :PublicationReferences (for [publication (select md-data-id-el publication-xpath)
                                  :let [role-xpath "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty[gmd:role/gmd:CI_RoleCode/@codeListValue='%s']"
                                        select-party (fn [name xpath]
                                                       (char-string-value publication
                                                                          (str (format role-xpath name) xpath)))]]
                              {:Author (select-party "author" "/gmd:organisationName")
                               :PublicationDate (str (date-at publication
                                                              (str "gmd:date/gmd:CI_Date[gmd:dateType/gmd:CI_DateTypeCode/@codeListValue='publication']/"
                                                                   "gmd:date/gco:Date")))
                               :Title (char-string-value publication "gmd:title")
                               :Series (char-string-value publication "gmd:series/gmd:CI_Series/gmd:name")
                               :Edition (char-string-value publication "gmd:edition")
                               :Issue (char-string-value publication "gmd:series/gmd:CI_Series/gmd:issueIdentification")
                               :Pages (char-string-value publication "gmd:series/gmd:CI_Series/gmd:page")
                               :Publisher (select-party "publisher" "/gmd:organisationName")
                               :ISBN (char-string-value publication "gmd:ISBN")
                               :DOI {:DOI (char-string-value publication "gmd:identifier/gmd:MD_Identifier/gmd:code")}
                               :OtherReferenceDetails (char-string-value publication "gmd:otherCitationDetails")})
     :AncillaryKeywords (descriptive-keywords-type-not-equal md-data-id-el ["place" "temporal" "project" "platform" "instrument"])
     :RelatedUrls (dru/parse-related-urls doc)}))

(defn iso19115-2-xml-to-umm-c
  "Returns UMM-C collection record from ISO19115-2 collection XML document."
  [metadata]
  (js/coerce (parse-iso19115-xml metadata)))


