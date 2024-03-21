(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2
  "Defines mappings from UMM records into ISO19115-2 XML."
  (:require
   [clojure.string :as string]
   [cmr.common.date-time-parser :as p]
   [cmr.common.util :as util]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.date-util :as date-util]
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
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.additional-attribute :as aa]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.data-contact :as data-contact]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.metadata-association :as ma]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial :as spatial]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.tiling-system :as tiling]
   [cmr.umm-spec.util :as su :refer [char-string]]))

(def iso19115-2-xml-namespaces
  {:xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xs "http://www.w3.org/2001/XMLSchema"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation (str "http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd "
                            "http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd "
                            "http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd "
                            "http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd "
                            "http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd "
                            "http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd "
                            "http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd "
                            "http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd "
                            "http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd "
                            "http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd")})

(def iso-topic-categories
  #{"farming"
    "biota"
    "boundaries"
    "climatologyMeteorologyAtmosphere"
    "economy"
    "elevation"
    "environment"
    "geoscientificInformation"
    "health"
    "imageryBaseMapsEarthCover"
    "intelligenceMilitary"
    "inlandWaters"
    "location"
    "oceans"
    "planningCadastre"
    "society"
    "structure"
    "transportation"
    "utilitiesCommunication"})

(defn- generate-projects-keywords
  "Returns the content generator instructions for descriptive keywords of the given projects."
  [projects]
  (let [project-keywords (map iso/generate-title projects)]
    (kws/generate-iso19115-descriptive-keywords "project" project-keywords)))

(defn- generate-data-dates
  "Returns ISO XML elements for the DataDates of given UMM collection."
  [c]
  ;; Use a default value if none present in the UMM record
  (let [dates (or (:DataDates c) [{:Type "CREATE" :Date date-util/default-date-value}])]
    (for [date dates
          :let [type-code (get iso/iso-date-type-codes (:Type date))
                date-value (or (:Date date) date-util/default-date-value)]]
      [:gmd:date
       [:gmd:CI_Date
        [:gmd:date
         [:gco:DateTime date-value]]
        [:gmd:dateType
         [:gmd:CI_DateTypeCode {:codeList (str (:ngdc iso/code-lists) "#CI_DateTypeCode")
                                :codeListValue type-code} type-code]]]])))

(defn- generate-datestamp
 "Return the ISO datestamp from metadata dates. Use update date if available, then creation date
 if available, or a default if neither are populated."
 [c]
 (when-let [datestamp (or (date-util/metadata-update-date c)
                          (date-util/metadata-create-date c)
                          date-util/parsed-default-date)]
  [:gmd:dateStamp
   [:gco:DateTime (p/clj-time->date-time-str datestamp)]]))

(defn- generate-metadata-dates
  "Returns ISO datestamp and XML elements for Metadata Dates of the given UMM collection.
  ParentEntity, rule, and source are required under MD_ExtendedElementInformation, but are just
  populated with empty since they are not needed for the Metadata Dates"
  [c]
  (for [date (:MetadataDates c)]
   [:gmd:metadataExtensionInfo
    [:gmd:MD_MetadataExtensionInformation
     [:gmd:extendedElementInformation
      [:gmd:MD_ExtendedElementInformation
       [:gmd:name
        [:gco:CharacterString (iso/get-iso-metadata-type-name (:Type date))]]
       [:gmd:definition
        [:gco:CharacterString (get iso/iso-metadata-type-definitions (:Type date))]]
       [:gmd:dataType
        [:gmd:MD_DatatypeCode
         {:codeList ""
          :codeListValue ""} "Date"]]
       [:gmd:domainValue
        [:gco:CharacterString (p/clj-time->date-time-str (:Date date))]]
       [:gmd:parentEntity {:gco:nilReason "inapplicable"}]
       [:gmd:rule {:gco:nilReason "inapplicable"}]
       [:gmd:source {:gco:nilReason "inapplicable"}]]]]]))

(defn iso-topic-value->sanitized-iso-topic-category
  "Ensures an uncontrolled IsoTopicCategory value is on the schema-defined list or substitues a
  default value."
  [category-value]
  (get iso-topic-categories category-value "location"))

(defn- generate-doi-for-publication-reference
  "Generates the DOI portion of a publication reference."
  [pub-ref]
  (let [doi (util/remove-nil-keys (:DOI pub-ref))]
    (when (seq doi)
      [:gmd:identifier
       [:gmd:MD_Identifier
        [:gmd:code (char-string (:DOI doi))]
        [:gmd:description (char-string "DOI")]]])))

(defn- generate-publication-references
  "Returns the publication references."
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
        (generate-doi-for-publication-reference pub-ref)
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
        (when-let [online-resource (:OnlineResource pub-ref)]
         [:gmd:citedResponsibleParty
          [:gmd:CI_ResponsibleParty
           [:gmd:contactInfo
            [:gmd:CI_Contact
             [:gmd:onlineResource
              [:gmd:CI_OnlineResource
               [:gmd:linkage
                [:gmd:URL (:Linkage online-resource)]]
               [:gmd:protocol (char-string (:Protocol online-resource))]
               [:gmd:applicationProfile (char-string (:ApplicationProfile online-resource))]
               (when-let [name (:Name online-resource)]
                 [:gmd:name (char-string name)])
               (when-let [description (:Description online-resource)]
                 [:gmd:description (char-string (str description " PublicationReference:"))])
               [:gmd:function
                [:gmd:CI_OnLineFunctionCode
                 {:codeList (str (:iso iso/code-lists) "#CI_OnLineFunctionCode")
                  :codeListValue ""} (:Function online-resource)]]]]]]
           [:gmd:role
            [:gmd:CI_RoleCode
             {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
              :codeListValue "resourceProvider"} "resourceProvider"]]]])
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
         :codeListValue "crossReference"} "crossReference"]]]]))

(defn extent-description-string
  "Returns the ISO extent description string (a \"key=value,key=value\" string) for the given UMM-C
  collection record."
  [c]
  (let [m {"SpatialCoverageType" (-> c :SpatialExtent :SpatialCoverageType)
           "SpatialGranuleSpatialRepresentation" (-> c :SpatialExtent :GranuleSpatialRepresentation)
           "CoordinateSystem" (-> c :SpatialExtent :HorizontalSpatialDomain :Geometry :CoordinateSystem)}]
    (string/join "," (for [[k v] m
                           :when (some? v)]
                      (str k "=" (string/replace v #"[,=]" ""))))))

(defn generate-other-identifer
  "Returns the ISO structure for UMM OtherIdentifiers"
  [other-identifier]
  [:gmd:identifier
   [:gmd:MD_Identifier
    [:gmd:code [:gco:CharacterString (:Identifier other-identifier)]]
    [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.otheridentifier"]]
    [:gmd:description [:gco:CharacterString (if (:DescriptionOfOtherType other-identifier)
                                              (format "Type: %s DescriptionOfOtherType: %s" (:Type other-identifier) (:DescriptionOfOtherType other-identifier))
                                              (format "Type: %s" (:Type other-identifier)))]]]])

(defn generate-file-naming-convention
  "Returns the ISO structure for UMM file naming convention."
  [fnc]
  (let [spec (if (:Description fnc)
               (format "FileNameConvention: %s ConventionDescription: %s" (:Convention fnc) (:Description fnc))
               (format "FileNameConvention: %s" (:Convention fnc)))]
    [:gmd:resourceFormat
     [:gmd:MD_Format
      [:gmd:name (char-string "FileNamingConvention")]
      [:gmd:version {:gco:nilReason "inapplicable"}]
      [:gmd:specification (char-string spec)]]]))

(defn convert-temporal-resolution-to-iso
  "Check and convert the UMM temporal resolution to satisfy the ISO schema."
  [resolution]
  (let [unit (util/safe-lowercase (:Unit resolution))
        value (:Value resolution)
        value (if (= "week" unit) ;; Convert week to day
                (* 7 value)
                value)
        unit (case unit
               "week" "day"
               "diurnal" "day"
               unit)]
    {:Value value
     :Unit unit}))

(defn create-iso-temp-res
  "Creates a temporary temporal resolution map structure to more easily
  create ISO temporal extent data."
  [idx resolution]
  (let [base {:Id (str "temporal_extent_" idx)}
        res (if (:Value resolution)
              (convert-temporal-resolution-to-iso resolution)
              resolution)]
    (if (:Value res)
      (merge base
             {:Begin ""
              :End ""
              :Value (:Value res)
              :Unit (:Unit res)})
      (merge base
             {:Instant ""
              :Unit (:Unit res)}))))

(defn create-iso-rdt
  "Creates a temporary temporal range date times map structure to more easily
  create ISO temporal extent data."
  [idx rdt ends-at-present? resolution]
  (let [begin (:BeginningDateTime rdt)
        end (:EndingDateTime rdt)
        base {:Id (str "temporal_extent_" idx)
              :Begin (su/nil-to-empty-string (when begin
                                               (if (= (type begin) java.lang.String)
                                                 begin
                                                 (p/clj-time->date-time-str begin))))
              :End (if ends-at-present?
                     {:indeterminatePosition "now"}
                     (su/nil-to-empty-string (when end
                                               (if (= (type end) java.lang.String)
                                                 end
                                                 (p/clj-time->date-time-str end)))))}]
    (if (:Value resolution)
      (let [res (convert-temporal-resolution-to-iso resolution)]
        (merge base
               {:Value (:Value res)
                :Unit (:Unit res)}))
      (if (:Unit resolution)
        (vector base (create-iso-temp-res idx resolution))
        base))))

(defn create-iso-sdt
  "Creates a temporary temporal single date times map structure to more easily
  create ISO temporal extent data."
  [idx sdt resolution]
  (let [base {:Id (str "temporal_extent_" idx)
              :Instant (when sdt
                         (if (= (type sdt) java.lang.String)
                           sdt
                           (p/clj-time->date-time-str sdt)))}]
    (if (:Unit resolution)
      (vector base (create-iso-temp-res idx resolution))
      base)))

(defn generate-iso-temporal-extents
  "Takes the temporal extent temporary structure and builds the ISO temporal extent."
  [extent-map]
  [:gmd:temporalElement {:uuidref (:Id extent-map)}
   [:gmd:EX_TemporalExtent
    [:gmd:extent
     (when (:Begin extent-map)
       (if (and (= "" (:Begin extent-map))
                (:Value extent-map))
         [:gml:TimePeriod {:gml:id (str (:Id extent-map) "_resolution")}
          [:gml:beginPosition ""]
          [:gml:endPosition ""]
          [:gml:timeInterval {:unit (:Unit extent-map)} (:Value extent-map)]]
         [:gml:TimePeriod {:gml:id (:Id extent-map)}
          [:gml:beginPosition (:Begin extent-map)]
          [:gml:endPosition (:End extent-map)]
          (when (:Value extent-map)
            [:gml:timeInterval {:unit (:Unit extent-map)} (:Value extent-map)])]))
     (when (:Instant extent-map)
       (if (and (= "" (:Instant extent-map))
                (:Unit extent-map))
         [:gml:TimeInstant {:gml:id (str (:Id extent-map) "_resolution")}
          [:gml:timePosition (:Unit extent-map)]]
         [:gml:TimeInstant {:gml:id (:Id extent-map)}
          [:gml:timePosition (:Instant extent-map)]]))]]])

(defn generate-temporal-umm-maps
  "Generates and returns ISO temporal extents from UMM-C temporal extents."
  [temporal-extents]
  (loop [extents temporal-extents
         cntr 1
         result nil]
    (if (seq extents)
      (let [extent (first extents)
            rdts (:RangeDateTimes extent)
            sdts (:SingleDateTimes extent)
            rdts-map (flatten (vector (map-indexed #(create-iso-rdt (+ cntr %)
                                                                    %2
                                                                    (:EndsAtPresentFlag extent)
                                                                    (:TemporalResolution extent))
                                                   rdts)))
            sdts-map (flatten (vector (map-indexed #(create-iso-sdt (+ cntr (count rdts) %)
                                                                    %2
                                                                    (:TemporalResolution extent))
                                                   sdts)))
            temp-res (when (and (:TemporalResolution extent)
                                (not (or rdts sdts)))
                       (create-iso-temp-res (+ cntr (count rdts) (count sdts))
                                            (:TemporalResolution extent)))
            results (conj result rdts-map sdts-map temp-res)]
        (recur (rest extents) (+ cntr (count rdts) (count sdts) (if temp-res 1 0)) results))
      result)))

(defn generate-temporal-extents
  "The starting point to generate the temporal extents. A temporary map structure is created first
  so that we can more easily put the data into the ISO structure."
  [temporal-extents]
  (let [umm-temporal-maps (generate-temporal-umm-maps temporal-extents)
        umm-temporal-maps (flatten (util/remove-nils-empty-maps-seqs umm-temporal-maps))]
    (doall (map generate-iso-temporal-extents umm-temporal-maps))))

(defn umm-c-to-iso19115-2-xml
  "Returns the generated ISO19115-2 xml from UMM collection record c."
  [c]
  (let [platforms (platform/platforms-with-id (:Platforms c))
        {additional-attributes :AdditionalAttributes
         abstract :Abstract
         version-description :VersionDescription
         processing-level :ProcessingLevel} c]
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
      (data-contact/generate-data-center-metadata-author-contact-persons (:DataCenters c))
      (data-contact/generate-metadata-author-contact-persons (:ContactPersons c))
      (if-let [archive-centers (data-contact/generate-archive-centers (:DataCenters c))]
        archive-centers
        [:gmd:contact {:gco:nilReason "missing"}])
      (generate-datestamp c)
      [:gmd:metadataStandardName (char-string "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data")]
      [:gmd:metadataStandardVersion (char-string "ISO 19115-2:2009(E)")]
      (spatial/generate-spatial-representation-infos c)
      (spatial/coordinate-system-element c)
      (generate-metadata-dates c)
      [:gmd:identificationInfo
        [:gmd:MD_DataIdentification
          [:gmd:citation
           [:gmd:CI_Citation
            [:gmd:title (char-string (:EntryTitle c))]
            (generate-data-dates c)
            [:gmd:edition (char-string (:Version c))]
            (collection-citation/convert-date c)
            [:gmd:identifier
              [:gmd:MD_Identifier
                [:gmd:code (char-string (:ShortName c))]
                [:gmd:codeSpace (char-string "gov.nasa.esdis.umm.shortname")]
                [:gmd:description (char-string "Short Name")]]]
            (doi/generate-doi c)
            (when-let [collection-data-type (:CollectionDataType c)]
              [:gmd:identifier
                [:gmd:MD_Identifier
                  [:gmd:code [:gco:CharacterString collection-data-type]]
                  [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.collectiondatatype"]]
                  [:gmd:description [:gco:CharacterString "Collection Data Type"]]]])
            (let [standard-product (:StandardProduct c)]
              (when (some? standard-product)
                [:gmd:identifier
                  [:gmd:MD_Identifier
                    [:gmd:code [:gco:CharacterString standard-product]]
                    [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.standardproduct"]]
                    [:gmd:description [:gco:CharacterString "Standard Product"]]]]))
            (when (:OtherIdentifiers c)
              (map #(generate-other-identifer %) (:OtherIdentifiers c)))
            (when-let [data-maturity (:DataMaturity c)]
              [:gmd:identifier
               [:gmd:MD_Identifier
                [:gmd:code [:gco:CharacterString data-maturity]]
                [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.datamaturity"]]
                [:gmd:description [:gco:CharacterString "Data Maturity"]]]])
            (collection-citation/convert-creator c)
            (collection-citation/convert-editor c)
            (collection-citation/convert-publisher c)
            (collection-citation/convert-release-place c)
            (collection-citation/convert-online-resource c)
            (collection-citation/convert-data-presentation-form c)
            (collection-citation/convert-series-name-and-issue-id c)
            (collection-citation/convert-other-citation-details c)]]
          [:gmd:abstract (char-string (if (or abstract version-description)
                                        (str abstract iso/version-description-separator version-description)
                                        su/not-provided))]
          [:gmd:purpose {:gco:nilReason "missing"} (char-string (:Purpose c))]
          (collection-progress/generate-collection-progress c)
          (data-contact/generate-data-centers (:DataCenters c))
          (data-contact/generate-data-center-contact-persons (:DataCenters c))
          (data-contact/generate-data-center-contact-groups (:DataCenters c))
          (data-contact/generate-contact-persons (:ContactPersons c))
          (data-contact/generate-contact-groups (:ContactGroups c))
          (sdru/generate-browse-urls c)
          (archive-and-dist-info/generate-file-archive-info c)
          (when-let [fnc (:FileNamingConvention c)]
            (generate-file-naming-convention fnc))
          (generate-projects-keywords (:Projects c))
          (kws/generate-iso19115-descriptive-keywords
           kws/science-keyword-type (map kws/science-keyword->iso-keyword-string (:ScienceKeywords c)))
          (kws/generate-iso19115-descriptive-keywords
           kws/location-keyword-type (map kws/location-keyword->iso-keyword-string (:LocationKeywords c)))
          (kws/generate-iso19115-descriptive-keywords "temporal" (:TemporalKeywords c))
          (kws/generate-iso19115-descriptive-keywords nil (:AncillaryKeywords c))
          (platform/generate-platform-keywords platforms)
          (platform/generate-instrument-keywords platforms)
          (use-constraints/generate-user-constraints c)
          (ma/generate-non-source-metadata-associations c)
          (generate-publication-references (:PublicationReferences c))
          (sdru/generate-publication-related-urls c)
          (doi/generate-associated-dois c)
          (doi/generate-previous-version c)
          [:gmd:language (char-string (or (:DataLanguage c) "eng"))]
          (iso-topic-categories/generate-iso-topic-categories c)
          (when (:TilingIdentificationSystems c)
            [:gmd:extent
             [:gmd:EX_Extent {:id "TilingIdentificationSystem"}
              [:gmd:description
               [:gco:CharacterString "Tiling Identitfication System"]]
              (tiling/tiling-system-elements c)]])
          [:gmd:extent
           [:gmd:EX_Extent {:id "boundingExtent"}
            [:gmd:description
             [:gco:CharacterString (extent-description-string c)]]
            (spatial/generate-zone-identifier c)
            (spatial/spatial-extent-elements c)
            (spatial/generate-resolution-and-coordinate-system-description c)
            (spatial/generate-resolution-and-coordinate-system-geodetic-model c)
            (spatial/generate-resolution-and-coordinate-system-local-coords c)
            (spatial/generate-resolution-and-coordinate-system-horizontal-data-resolutions c)
            (spatial/generate-vertical-domain c)
            (spatial/generate-orbit-parameters c)
            (spatial/generate-orbit-parameters-foot-prints c)
            (generate-temporal-extents (:TemporalExtents c))]]
          (when processing-level
            [:gmd:processingLevel
             (proc-level/generate-iso-processing-level processing-level)])]]
      (sdru/generate-service-related-url (:RelatedUrls c))
      (aa/generate-content-info-additional-attributes additional-attributes)
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
              [:gco:Real (:PrecisionOfSeconds (first (:TemporalExtents c)))]]]]]]]
        (when-let [quality (:Quality c)]
          [:gmd:report
           [:gmd:DQ_QuantitativeAttributeAccuracy
            [:gmd:evaluationMethodDescription (char-string quality)]
            [:gmd:result {:gco:nilReason "missing"}]]])
        [:gmd:lineage
         [:gmd:LI_Lineage
          (aa/generate-data-quality-info-additional-attributes additional-attributes)
          (when-let [processing-centers (data-contact/generate-processing-centers (:DataCenters c))]
            [:gmd:processStep
             [:gmd:LI_ProcessStep
              [:gmd:description {:gco:nilReason "missing"}]
              processing-centers]])
          (ma/generate-source-metadata-associations c)]]]]
      [:gmi:acquisitionInformation
       [:gmi:MI_AcquisitionInformation
        (platform/generate-instruments platforms)
        (platform/generate-child-instruments platforms)
        (project/generate-projects (:Projects c))
        (platform/generate-platforms platforms)]]])))
