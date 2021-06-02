(ns cmr.umm-spec.umm-to-xml-mappings.iso-smap
  "Defines mappings from UMM records into ISO SMAP XML."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.date-util :as du]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.archive-and-dist-info :as archive-and-dist-info]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.collection-citation :as collection-citation]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.collection-progress :as collection-progress]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.distributions-related-url :as sdru]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.doi :as doi]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.iso-topic-categories :as iso-topic-categories]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.platform :as platform]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.processing-level :as proc-level]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.project-element :as project]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.use-constraints :as use-constraints]
   [cmr.umm-spec.umm-to-xml-mappings.iso-smap.collection-citation :as smap-collection-citation]
   [cmr.umm-spec.umm-to-xml-mappings.iso-smap.data-contact :as data-contact]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial :as iso19115-spatial-conversion]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.tiling-system :as tiling]
   [cmr.umm-spec.util :as su :refer [with-default char-string]]))

(def iso-smap-xml-namespaces
  {:xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"})

(defn- generate-spatial-extent
  "Returns ISO SMAP SpatialExtent content generator instructions"
  [spatial-extent]
  (for [br (get-in spatial-extent [:HorizontalSpatialDomain :Geometry :BoundingRectangles])]
    [:gmd:geographicElement
     [:gmd:EX_GeographicBoundingBox
      [:gmd:extentTypeCode
       [:gco:Boolean 1]]
      [:gmd:westBoundLongitude
       [:gco:Decimal (:WestBoundingCoordinate br)]]
      [:gmd:eastBoundLongitude
       [:gco:Decimal (:EastBoundingCoordinate br)]]
      [:gmd:southBoundLatitude
       [:gco:Decimal (:SouthBoundingCoordinate br)]]
      [:gmd:northBoundLatitude
       [:gco:Decimal (:NorthBoundingCoordinate br)]]]]))

(defn- generate-data-dates
  "Returns ISO SMAP XML elements for the DataDates of given UMM collection.
  If no DataDates are present, use the default date value as the CREATE datetime."
  [c]
  (let [dates (or (:DataDates c) [{:Type "CREATE" :Date du/default-date-value}])]
    (for [date dates
          :let [type-code (get iso/iso-date-type-codes (:Type date))
                date-value (or (:Date date) du/default-date-value)]]
      [:gmd:date
       [:gmd:CI_Date
        [:gmd:date
         [:gco:DateTime date-value]]
        [:gmd:dateType
         [:gmd:CI_DateTypeCode {:codeList (str (:iso iso/code-lists) "#CI_DateTypeCode")
                                :codeListValue type-code} type-code]]]])))

(defn- generate-projects-keywords
  "Returns the content generator instructions for descriptive keywords of the given projects."
  [projects]
  (let [project-keywords (map iso/generate-title projects)]
    (kws/generate-iso-smap-descriptive-keywords "project" project-keywords)))

(defn umm-c-to-iso-smap-xml
  "Returns ISO SMAP XML from UMM-C record c."
  [c]
  (let [platforms (platform/platforms-with-id (:Platforms c))
        {processing-level :ProcessingLevel} c]
    (xml
     [:gmd:DS_Series
      iso-smap-xml-namespaces
      [:gmd:composedOf {:gco:nilReason "inapplicable"}]
      [:gmd:seriesMetadata
       [:gmi:MI_Metadata
        [:gmd:language (char-string "eng")]
        [:gmd:contact {:xlink:href "#alaskaSARContact"}]
        (data-contact/generate-metadata-authors c)
        [:gmd:dateStamp
         [:gco:Date "2013-01-02"]]
        [:gmd:identificationInfo
         [:gmd:MD_DataIdentification
          [:gmd:citation
           [:gmd:CI_Citation
            (smap-collection-citation/convert-title c)
            (generate-data-dates c)
            (smap-collection-citation/convert-version c)
            (collection-citation/convert-date c)
            [:gmd:identifier
             [:gmd:MD_Identifier
              [:gmd:code (char-string (:ShortName c))]
              [:gmd:description [:gco:CharacterString "The ECS Short Name"]]]]
            [:gmd:identifier
             [:gmd:MD_Identifier
              [:gmd:code (char-string (:Version c))]
              [:gmd:description [:gco:CharacterString "The ECS Version ID"]]]]
            (doi/generate-doi c)
            (when-let [collection-data-type (:CollectionDataType c)]
             [:gmd:identifier
              [:gmd:MD_Identifier
               [:gmd:code [:gco:CharacterString collection-data-type]]
               [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.collectiondatatype"]]
               [:gmd:description [:gco:CharacterString "Collection Data Type"]]]])
            (collection-citation/convert-creator c)
            (collection-citation/convert-editor c)
            (collection-citation/convert-publisher c)
            (collection-citation/convert-release-place c)
            (collection-citation/convert-online-resource c)
            (collection-citation/convert-data-presentation-form c)
            (collection-citation/convert-series-name-and-issue-id c)
            (collection-citation/convert-other-citation-details c)]]
          [:gmd:abstract (char-string (or (:Abstract c) su/not-provided))]
          [:gmd:purpose {:gco:nilReason "missing"} (char-string (:Purpose c))]
          (collection-progress/generate-collection-progress c)
          (data-contact/generate-data-centers c "DISTRIBUTOR" "ORIGINATOR" "ARCHIVER")
          (data-contact/generate-data-centers-contact-persons c "DISTRIBUTOR" "ORIGINATOR" "ARCHIVER")
          (data-contact/generate-contact-persons (:ContactPersons c))
          (generate-projects-keywords (:Projects c))
          (kws/generate-iso-smap-descriptive-keywords
           kws/science-keyword-type (map kws/science-keyword->iso-keyword-string (:ScienceKeywords c)))
          (kws/generate-iso-smap-descriptive-keywords
           kws/location-keyword-type (map kws/location-keyword->iso-keyword-string (:LocationKeywords c)))
          [:gmd:descriptiveKeywords
           [:gmd:MD_Keywords
            (for [platform (:Platforms c)]
              [:gmd:keyword
               (char-string (kws/smap-keyword-str platform))])
            (for [instrument (distinct (mapcat :Instruments (:Platforms c)))]
              [:gmd:keyword
               (char-string (kws/smap-keyword-str instrument))])]]
          (use-constraints/generate-user-constraints c)
          (doi/generate-associated-dois c)
          [:gmd:language (char-string (or (:DataLanguage c) "eng"))]
          (iso-topic-categories/generate-iso-topic-categories c)
          (when (first (:TilingIdentificationSystems c))
            [:gmd:extent
              [:gmd:EX_Extent {:id "TilingIdentificationSystem"}
                [:gmd:description
                  [:gco:CharacterString "Tiling Identitfication System"]]
                (tiling/tiling-system-elements c)]])
          [:gmd:extent
           [:gmd:EX_Extent
            (generate-spatial-extent (:SpatialExtent c))
            (iso19115-spatial-conversion/generate-vertical-domain c)
            (for [temporal (:TemporalExtents c)
                  rdt (:RangeDateTimes temporal)]
              [:gmd:temporalElement
               [:gmd:EX_TemporalExtent
                [:gmd:extent
                 [:gml:TimePeriod {:gml:id (su/generate-id)}
                  [:gml:beginPosition (:BeginningDateTime rdt)]
                  (let [ends-at-present (:EndsAtPresentFlag temporal)]
                    [:gml:endPosition (if ends-at-present
                                        {:indeterminatePosition "now"}
                                        {})
                     (when-not ends-at-present
                       (or (:EndingDateTime rdt) ""))])]]]])
            (for [temporal (:TemporalExtents c)
                  date (:SingleDateTimes temporal)]
              [:gmd:temporalElement
               [:gmd:EX_TemporalExtent
                [:gmd:extent
                 [:gml:TimeInstant {:gml:id (su/generate-id)}
                  [:gml:timePosition date]]]]])]]

          (when processing-level
           [:gmd:processingLevel
            (proc-level/generate-iso-processing-level processing-level)])]]
        [:gmd:identificationInfo
         [:gmd:MD_DataIdentification
          [:gmd:citation
           [:gmd:CI_Citation
            [:gmd:title (char-string "DataSetId")]
            (generate-data-dates c)
            (data-contact/generate-data-centers c "PROCESSOR")
            (data-contact/generate-data-centers-contact-persons c "PROCESSOR")]]
          [:gmd:abstract (char-string "DataSetId")]
          (sdru/generate-browse-urls c)
          (archive-and-dist-info/generate-file-archive-info c)
          [:gmd:aggregationInfo
           [:gmd:MD_AggregateInformation
            [:gmd:aggregateDataSetIdentifier
             [:gmd:MD_Identifier
              [:gmd:code (char-string (:EntryTitle c))]]]
            [:gmd:associationType
             [:gmd:DS_AssociationTypeCode {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
                                           :codeListValue "largerWorkCitation"}
              "largerWorkCitation"]]]]
          (sdru/generate-publication-related-urls c)
          [:gmd:language (char-string "eng")]]]
        (sdru/generate-service-related-url (:RelatedUrls c))
        (when processing-level
         [:gmd:contentInfo
          [:gmd:MD_ImageDescription
           [:gmd:attributeDescription ""]
           [:gmd:contentType ""]
           [:gmd:processingLevelCode
             (proc-level/generate-iso-processing-level processing-level)]]])
        (let [related-url-distributions (sdru/generate-distributions c)
              file-dist-info-formats (archive-and-dist-info/generate-file-dist-info-formats c)
              file-dist-info-medias (archive-and-dist-info/generate-file-dist-info-medias c)
              file-dist-info-total-coll-sizes (archive-and-dist-info/generate-file-dist-info-total-coll-sizes c)
              file-dist-info-average-sizes (archive-and-dist-info/generate-file-dist-info-average-file-sizes c)
              file-dist-info-distributors (archive-and-dist-info/generate-file-dist-info-distributors c)
              direct-dist-info (archive-and-dist-info/generate-direct-dist-info-distributors c)]
          (when (or file-dist-info-formats
                    related-url-distributions
                    file-dist-info-distributors
                    file-dist-info-medias
                    file-dist-info-total-coll-sizes
                    file-dist-info-average-sizes
                    file-dist-info-distributors
                    direct-dist-info)
            [:gmd:distributionInfo
             [:gmd:MD_Distribution
              file-dist-info-formats
              related-url-distributions
              file-dist-info-distributors
              direct-dist-info
              file-dist-info-medias
              file-dist-info-total-coll-sizes
              file-dist-info-average-sizes]]))
        [:gmd:dataQualityInfo
         [:gmd:DQ_DataQuality
          [:gmd:scope
           [:gmd:DQ_Scope
            [:gmd:level
             [:gmd:MD_ScopeCode
              {:codeList (str (:iso iso/code-lists) "#MD_ScopeCode")
               :codeListValue "series"}
              "series"]]]]
          (when-let [quality (:Quality c)]
            [:gmd:report
             [:gmd:DQ_QuantitativeAttributeAccuracy
              [:gmd:evaluationMethodDescription (char-string quality)]
              [:gmd:result {:gco:nilReason "missing"}]]])]]
        [:gmi:acquisitionInformation
         [:gmi:MI_AcquisitionInformation
          (platform/generate-instruments platforms)
          (platform/generate-child-instruments platforms)
          (project/generate-projects (:Projects c))
          (platform/generate-platforms platforms)]]]]])))
