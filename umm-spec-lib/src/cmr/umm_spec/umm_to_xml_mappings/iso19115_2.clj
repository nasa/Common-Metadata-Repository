(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2
  "Defines mappings from UMM records into ISO19115-2 XML."
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial :as spatial]
            [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.iso-utils :as iso-utils]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.platform :as platform]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.distributions-related-url :as dru]))


(def iso19115-2-xml-namespaces
  {:xmlns:xs "http://www.w3.org/2001/XMLSchema"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:swe "http://schemas.opengis.net/sweCommon/2.0/"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"})

(defn- generate-data-dates
  "Returns ISO XML elements for the DataDates of given UMM collection."
  [c]
  (for [date (:DataDates c)
        :let [type-code (get iso/iso-date-type-codes (:Type date))
              date-value (:Date date)]]
    [:gmd:date
     [:gmd:CI_Date
      [:gmd:date
       [:gco:DateTime date-value]]
      [:gmd:dateType
       [:gmd:CI_DateTypeCode {:codeList (str (:ngdc iso/code-lists) "#CI_DateTypeCode")
                              :codeListValue type-code} type-code]]]]))

(def attribute-data-type-code-list
  "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode")

(defn- generate-projects-keywords
  "Returns the content generator instructions for descriptive keywords of the given projects."
  [projects]
  (let [project-keywords (map iso/generate-title projects)]
    (iso/generate-descriptive-keywords "project" project-keywords)))

(defn- generate-projects
  [projects]
  (for [proj projects]
    (let [{short-name :ShortName} proj]
      [:gmi:operation
       [:gmi:MI_Operation
        [:gmi:description
         (char-string (iso/generate-title proj))]
        [:gmi:identifier
         [:gmd:MD_Identifier
          [:gmd:code
           (char-string short-name)]]]
        [:gmi:status ""]
        [:gmi:parentOperation {:gco:nilReason "inapplicable"}]]])))



(defn- generate-publication-references
  [pub-refs]
  (for [pub-ref pub-refs
        ;; Title and PublicationDate are required fields in ISO
        :when (and (:Title pub-ref) (:PublicationDate pub-ref))]
    [:gmd:aggregationInfo
     [:gmd:MD_AggregateInformation
      [:gmd:aggregateDataSetName
       [:gmd:CI_Citation
        [:gmd:title (char-string (:Title pub-ref))]
        (when (:PublicationDate pub-ref)
          [:gmd:date
           [:gmd:CI_Date
            [:gmd:date
             [:gco:Date (second (re-matches #"(\d\d\d\d-\d\d-\d\d)T.*" (str (:PublicationDate pub-ref))))]]
            [:gmd:dateType
             [:gmd:CI_DateTypeCode
              {:codeList (str (:iso iso/code-lists) "#CI_DateTypeCode")
               :codeListValue "publication"} "publication"]]]])
        [:gmd:edition (char-string (:Edition pub-ref))]
        (when (:DOI pub-ref)
          [:gmd:identifier
           [:gmd:MD_Identifier
            [:gmd:code (char-string (get-in pub-ref [:DOI :DOI]))]
            [:gmd:description (char-string "DOI")]]])
        [:gmd:citedResponsibleParty
         [:gmd:CI_ResponsibleParty
          [:gmd:organisationName (char-string (:Author pub-ref))]
          [:gmd:role
           [:gmd:CI_RoleCode
            {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
             :codeListValue "author"} "author"]]]]
        [:gmd:citedResponsibleParty
         [:gmd:CI_ResponsibleParty
          [:gmd:organisationName (char-string (:Publisher pub-ref))]
          [:gmd:role
           [:gmd:CI_RoleCode
            {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
             :codeListValue "publisher"} "publication"]]]]
        [:gmd:series
         [:gmd:CI_Series
          [:gmd:name (char-string (:Series pub-ref))]
          [:gmd:issueIdentification (char-string (:Issue pub-ref))]
          [:gmd:page (char-string (:Pages pub-ref))]]]
        [:gmd:otherCitationDetails (char-string (:OtherReferenceDetails pub-ref))]
        [:gmd:ISBN (char-string (:ISBN pub-ref))]]]
      [:gmd:associationType
       [:gmd:DS_AssociationTypeCode
        {:codeList (str (:ngdc iso/code-lists) "#DS_AssociationTypeCode")
         :codeListValue "Input Collection"} "Input Collection"]]]]))

(defn extent-description-string
  "Returns the ISO extent description string (a \"key=value,key=value\" string) for the given UMM-C
  collection record."
  [c]
  (let [vsd (first (-> c :SpatialExtent :VerticalSpatialDomains))
        m {"VerticalSpatialDomainType" (:Type vsd)
           "VerticalSpatialDomainValue" (:Value vsd)
           "SpatialCoverageType" (-> c :SpatialExtent :SpatialCoverageType)
           "SpatialGranuleSpatialRepresentation" (-> c :SpatialExtent :GranuleSpatialRepresentation)}]
    (str/join "," (for [[k v] m
                        :when (some? v)]
                    (str k "=" (str/replace v #"[,=]" ""))))))

(defn- science-keyword->iso-keyword-string
  "Returns an ISO science keyword string from the given science keyword."
  [science-keyword]
  (let [{category :Category
         topic :Topic
         term :Term
         variable-level-1 :VariableLevel1
         variable-level-2 :VariableLevel2
         variable-level-3 :VariableLevel3
         detailed-variable :DetailedVariable} science-keyword]
    (str/join iso-utils/keyword-separator (map #(or % iso-utils/nil-science-keyword-field)
                                               [category topic term variable-level-1 variable-level-2
                                                variable-level-3 detailed-variable]))))

(defn umm-c-to-iso19115-2-xml
  "Returns the generated ISO19115-2 xml from UMM collection record c."
  [c]
  (let [platforms (platform/platforms-with-id (:Platforms c))]
    (xml
      [:gmi:MI_Metadata
       iso19115-2-xml-namespaces
       [:gmd:fileIdentifier (char-string (:EntryTitle c))]
       [:gmd:language (char-string "eng")]
       [:gmd:characterSet
        [:gmd:MD_CharacterSetCode {:codeList (str (:ngdc iso/code-lists) "#MD_CharacterSetCode")
                                   :codeListValue "utf8"} "utf8"]]
       [:gmd:hierarchyLevel
        [:gmd:MD_ScopeCode {:codeList (str (:ngdc iso/code-lists) "#MD_ScopeCode")
                            :codeListValue "series"} "series"]]
       [:gmd:contact {:gco:nilReason "missing"}]
       [:gmd:dateStamp
        [:gco:DateTime "2014-08-25T15:25:44.641-04:00"]]
       [:gmd:metadataStandardName (char-string "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data")]
       [:gmd:metadataStandardVersion (char-string "ISO 19115-2:2009(E)")]
       (spatial/coordinate-system-element c)
       [:gmd:identificationInfo
        [:gmd:MD_DataIdentification
         [:gmd:citation
          [:gmd:CI_Citation
           [:gmd:title (char-string (:EntryTitle c))]
           (generate-data-dates c)
           [:gmd:identifier
            [:gmd:MD_Identifier
             [:gmd:code (char-string (:EntryId c))]
             [:gmd:version (char-string (:Version c))]]]]]
         [:gmd:abstract (char-string (:Abstract c))]
         [:gmd:purpose {:gco:nilReason "missing"} (char-string (:Purpose c))]
         [:gmd:status
          (when-let [collection-progress (:CollectionProgress c)]
            [:gmd:MD_ProgressCode
             {:codeList (str (:ngdc iso/code-lists) "#MD_ProgressCode")
              :codeListValue (str/lower-case collection-progress)}
             collection-progress])]
         (dru/generate-browse-urls c)
         (generate-projects-keywords (:Projects c))
         (iso/generate-descriptive-keywords
           "theme" (map science-keyword->iso-keyword-string (:ScienceKeywords c)))
         (iso/generate-descriptive-keywords "place" (:SpatialKeywords c))
         (iso/generate-descriptive-keywords "temporal" (:TemporalKeywords c))
         (iso/generate-descriptive-keywords (:AncillaryKeywords c))
         (platform/generate-platform-keywords platforms)
         (platform/generate-instrument-keywords platforms)
         [:gmd:resourceConstraints
          [:gmd:MD_LegalConstraints
           [:gmd:useLimitation (char-string (:UseConstraints c))]
           [:gmd:useLimitation
            [:gco:CharacterString (str "Restriction Comment:" (-> c :AccessConstraints :Description))]]
           [:gmd:otherConstraints
            [:gco:CharacterString (str "Restriction Flag:" (-> c :AccessConstraints :Value))]]]]
         (generate-publication-references (:PublicationReferences c))
         [:gmd:language (char-string (or (:DataLanguage c) "eng"))]
         (for [topic-category (:ISOTopicCategories c)]
           [:gmd:topicCategory
            [:gmd:MD_TopicCategoryCode topic-category]])
         [:gmd:extent
          [:gmd:EX_Extent {:id "boundingExtent"}
           [:gmd:description
            [:gco:CharacterString (extent-description-string c)]]
           (spatial/spatial-extent-elements c)
           (for [temporal (:TemporalExtents c)
                 rdt (:RangeDateTimes temporal)]
             [:gmd:temporalElement
              [:gmd:EX_TemporalExtent
               [:gmd:extent
                [:gml:TimePeriod {:gml:id (iso-utils/generate-id)}
                 [:gml:beginPosition (:BeginningDateTime rdt)]
                 [:gml:endPosition (su/nil-to-empty-string (:EndingDateTime rdt))]]]]])
           (for [temporal (:TemporalExtents c)
                 date (:SingleDateTimes temporal)]
             [:gmd:temporalElement
              [:gmd:EX_TemporalExtent
               [:gmd:extent
                [:gml:TimeInstant {:gml:id (iso-utils/generate-id)}
                 [:gml:timePosition date]]]]])]]
         [:gmd:processingLevel
          [:gmd:MD_Identifier
           [:gmd:code (char-string (-> c :ProcessingLevel :Id))]
           [:gmd:description (char-string (-> c :ProcessingLevel :ProcessingLevelDescription))]]]]]
       [:gmd:contentInfo
        [:gmd:MD_ImageDescription
         [:gmd:attributeDescription ""]
         [:gmd:contentType ""]
         [:gmd:processingLevelCode
          [:gmd:MD_Identifier
           [:gmd:code (char-string (-> c :ProcessingLevel :Id))]
           [:gmd:description (char-string (-> c :ProcessingLevel :ProcessingLevelDescription))]]]]]
       (dru/generate-distributions c)
       [:gmd:dataQualityInfo
        [:gmd:DQ_DataQuality
         [:gmd:scope
          [:gmd:DQ_Scope
           [:gmd:level
            [:gmd:MD_ScopeCode
             {:codeList (str (:ngdc iso/code-lists) "#MD_ScopeCode")
              :codeListValue "series"}
             "series"]]]]
         [:gmd:report
          [:gmd:DQ_AccuracyOfATimeMeasurement
           [:gmd:measureIdentification
            [:gmd:MD_Identifier
             [:gmd:code
              (char-string "PrecisionOfSeconds")]]]
           [:gmd:result
            [:gmd:DQ_QuantitativeResult
             [:gmd:valueUnit ""]
             [:gmd:value
              [:gco:Record {:xsi:type "gco:Real_PropertyType"}
               [:gco:Real (:PrecisionOfSeconds (first (:TemporalExtents c)))]]]]]]]]]
       [:gmi:acquisitionInformation
        [:gmi:MI_AcquisitionInformation
         (platform/generate-instruments platforms)
         (generate-projects (:Projects c))
         (platform/generate-platforms platforms)]]])))

