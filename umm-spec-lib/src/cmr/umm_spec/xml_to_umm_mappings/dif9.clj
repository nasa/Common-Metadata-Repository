(ns cmr.umm-spec.xml-to-umm-mappings.dif9
  "Defines mappings from DIF9 XML into UMM records"
  (:require
    [camel-snake-kebab.core :as csk]
    [clj-time.format :as f]
    [clojure.string :as string]
    [cmr.common.util :as util]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select]]
    [cmr.umm-spec.date-util :as date]
    [cmr.umm-spec.dif-util :as dif-util]
    [cmr.umm-spec.json-schema :as js]
    [cmr.umm-spec.models.umm-collection-models :as umm-coll-models]
    [cmr.umm-spec.models.umm-common-models :as cmn]
    [cmr.umm-spec.url :as url]
    [cmr.umm-spec.util :as su]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.additional-attribute :as aa]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.data-center :as center]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.data-contact :as contact]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.paleo-temporal :as pt]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.spatial-extent :as spatial]
    [cmr.umm-spec.xml-to-umm-mappings.get-umm-element :as get-umm-element]
    [cmr.umm-spec.versioning :as umm-spec-versioning]
    [cmr.umm.dif.date-util :refer [parse-dif-end-date]]))

(def coll-progress-mapping
  "Mapping from values supported for DIF9 Data_Set_Progress to UMM CollectionProgress."
  {"COMPLETE" "COMPLETE"
   "IN WORK"  "ACTIVE"
   "ACTIVE" "ACTIVE"
   "PLANNED" "PLANNED"
   "DEPRECATED" "DEPRECATED"
   "NOT APPLICABLE" "NOT APPLICABLE"})

(defn- parse-instruments
  "Returns the parsed instruments for the given xml doc."
  [doc]
  (su/parse-short-name-long-name doc "/DIF/Sensor_Name"))

(defn- parse-just-platforms
  "Returns the parsed platforms only (without instruments) for the given xml doc."
  [doc]
  (su/parse-short-name-long-name doc "/DIF/Source_Name"))

(defn- parse-platforms
  "Returns the parsed platforms with instruments added for the given xml doc."
  [doc sanitize?]
  (let [platforms (parse-just-platforms doc)
        instruments (parse-instruments doc)]
    ;; When there is only one platform in the collection, associate the instruments on that platform.
    ;; Otherwise, create a dummy platform to hold all instruments and add that to the platforms.
    (if (= 1 (count platforms))
      (map #(assoc % :Instruments instruments) platforms)
      (if instruments
        (conj platforms {:ShortName (when sanitize? su/not-provided)
                         :Instruments instruments})
        (or (seq platforms) (when sanitize? su/not-provided-platforms))))))

(defn- get-short-name
  "Returns the short-name from the given entry-id and version-id, where entry-id is
  in the form of <short-name>_<version-id>."
  [entry-id version-id]
  (let [version-suffix (str "_" version-id)
        short-name-length (- (count entry-id) (count version-suffix))]
    (if (and version-id
             (> short-name-length 0)
             (= (subs entry-id short-name-length) version-suffix))
      (subs entry-id 0 short-name-length)
      entry-id)))

(defn- parse-metadata-dates
  "Returns a list of metadata dates"
  [doc]
  (remove nil? [(date/parse-date-type-from-xml doc "DIF/DIF_Creation_Date" "CREATE")
                (date/parse-date-type-from-xml doc "DIF/Last_DIF_Revision_Date" "UPDATE")]))

(defn- parse-related-urls
  "Returns a list of related urls. Each URL will be put into its own RelatedUrl object comply with UMM spec v1.9"
  [doc sanitize?]
  (when-let [related-urls (seq (select doc "/DIF/Related_URL"))]
    (for [related-url related-urls
          url (values-at related-url "URL")
          :let [description (value-of related-url "Description")
                type (value-of related-url "URL_Content_Type/Type")
                subtype (value-of related-url "URL_Content_Type/Subtype")
                url-type (get dif-util/dif-url-content-type->umm-url-types
                              [type subtype] su/default-url-type)]]
      (merge
       url-type
       {:URL (url/format-url url sanitize?)
        :Description description}))))

(defn parse-temporal-extents
 "Return a list of temporal extents from the XML doc"
 [doc sanitize?]
 (if-let [temporals (select doc "/DIF/Temporal_Coverage")]
  [{:RangeDateTimes (doall
                     (for [temporal temporals]
                       {:BeginningDateTime (date/with-default (date-at-str temporal "Start_Date") sanitize?)
                        :EndingDateTime (parse-dif-end-date (value-of temporal "Stop_Date"))}))}]
  (when sanitize? su/not-provided-temporal-extents)))

(defn- parse-collection-citation
  "Parse the Collection Citation from XML Data Set Citation"
  [doc sanitize?]
  (let [data-set-citations (seq (select doc "/DIF/Data_Set_Citation"))]
    (for [data-set-citation data-set-citations
          :let [release-date (date/sanitize-and-parse-date (value-of data-set-citation "Dataset_Release_Date") sanitize?)]]
      {:Creator (value-of data-set-citation "Dataset_Creator")
       :Editor (value-of data-set-citation "Dataset_Editor")
       :Title  (value-of data-set-citation "Dataset_Title")
       :SeriesName (value-of data-set-citation "Dataset_Series_Name")
       :ReleaseDate (if sanitize?
                      (when (date/valid-date? release-date)
                        release-date)
                      release-date)
       :ReleasePlace (value-of data-set-citation "Dataset_Release_Place")
       :Publisher (value-of data-set-citation "Dataset_Publisher")
       :Version (value-of data-set-citation "Version")
       :IssueIdentification (value-of data-set-citation "Issue_Identification")
       :DataPresentationForm (value-of data-set-citation "Data_Presentation_Form")
       :OtherCitationDetails (value-of data-set-citation "Other_Citation_Details")
       :OnlineResource (when-let [linkage (value-of data-set-citation "Online_Resource")]
                         {:Linkage linkage})})))

(defn- parse-doi
  "Parse the DOI from the data citation section.  If the DOI does not exist then set the DOIs
   MissingReason and Explanation since it is required as of UMM-C 1.16.1"
  [doc]
  (let [first-doi
         (first (for [dsc (select doc "DIF/Data_Set_Citation")
                      :let [doi (value-of dsc "Dataset_DOI")]
                      :when doi]
                  {:DOI doi}))]
    (if first-doi
      first-doi
      {:MissingReason "Unknown"
       :Explanation "It is unknown if this record has a DOI."})))

(defn- parse-dif9-xml
  "Returns collection map from DIF9 collection XML document."
  [doc {:keys [sanitize?]}]
  (let [entry-id (value-of doc "/DIF/Entry_ID")
        version-id (first (remove nil? (for [dsc (select doc "DIF/Data_Set_Citation")]
                                         (value-of dsc "Version"))))
        short-name (get-short-name entry-id version-id)]
    {:EntryTitle (value-of doc "/DIF/Entry_Title")
     :DOI (parse-doi doc)
     :ShortName short-name
     :Version (or version-id (when sanitize? su/not-provided))
     :Abstract (su/truncate-with-default (value-of doc "/DIF/Summary/Abstract") su/ABSTRACT_MAX sanitize?)
     :CollectionDataType (value-of doc "/DIF/Extended_Metadata/Metadata[Name='CollectionDataType']/Value")
     :CollectionCitations (parse-collection-citation doc sanitize?)
     :Purpose (su/truncate (value-of doc "/DIF/Summary/Purpose") su/PURPOSE_MAX sanitize?)
     :DataLanguage (dif-util/dif-language->umm-language (value-of doc "/DIF/Data_Set_Language"))
     :MetadataDates (parse-metadata-dates doc)
     :ISOTopicCategories (dif-util/parse-iso-topic-categories doc)
     :TemporalKeywords (values-at doc "/DIF/Data_Resolution/Temporal_Resolution")
     :Projects (for [proj (select doc "/DIF/Project")]
                 {:ShortName (value-of proj "Short_Name")
                  :LongName (su/truncate (value-of proj "Long_Name") su/PROJECT_LONGNAME_MAX sanitize?)})
     :DirectoryNames (dif-util/parse-idn-node doc)
     :CollectionProgress (get-umm-element/get-collection-progress
                           coll-progress-mapping
                           doc
                           "/DIF/Data_Set_Progress"
                           sanitize?)
     :LocationKeywords  (let [lks (select doc "/DIF/Location")]
                          (for [lk lks]
                            {:Category (value-of lk "Location_Category")
                             :Type (value-of lk "Location_Type")
                             :Subregion1 (value-of lk "Location_Subregion1")
                             :Subregion2 (value-of lk "Location_Subregion2")
                             :Subregion3 (value-of lk "Location_Subregion3")
                             :DetailedLocation (value-of lk "Detailed_Location")}))
     :Quality (su/truncate (value-of doc "/DIF/Quality") su/QUALITY_MAX sanitize?)
     :AccessConstraints (dif-util/parse-access-constraints doc sanitize?)
     :UseConstraints (when-let [description (su/truncate
                                              (value-of doc "/DIF/Use_Constraints")
                                              su/USECONSTRAINTS_MAX
                                              sanitize?)]
                       (umm-coll-models/map->UseConstraintsType
                         {:Description description}))
     :Platforms (parse-platforms doc sanitize?)
     :TemporalExtents (parse-temporal-extents doc sanitize?)
     :PaleoTemporalCoverages (pt/parse-paleo-temporal doc)
     :SpatialExtent (spatial/parse-spatial-extent doc sanitize?)
     ;; umm-lib only has ProcessingLevelId and it is from Metadata Name "ProductLevelId"
     ;; Need to double check which implementation is correct.
     :ProcessingLevel {:Id
                       (su/with-default
                        (value-of doc
                                  "/DIF/Extended_Metadata/Metadata[Name='ProcessingLevelId']/Value")
                        sanitize?)

                       :ProcessingLevelDescription
                       (value-of doc "/DIF/Extended_Metadata/Metadata[Name='ProcessingLevelDescription']/Value")}

     :AdditionalAttributes (aa/xml-elem->AdditionalAttributes doc sanitize?)
     :PublicationReferences (for [pub-ref (select doc "/DIF/Reference")]
                              (into {} (map (fn [x]
                                              (if (keyword? x)
                                                [(csk/->PascalCaseKeyword x) (value-of pub-ref (str x))]
                                                x))
                                            [:Author
                                             [:PublicationDate (date/sanitize-and-parse-date (value-of pub-ref "Publication_Date") sanitize?)]
                                             :Title
                                             :Series
                                             :Edition
                                             :Volume
                                             :Issue
                                             :Report_Number
                                             :Publication_Place
                                             :Publisher
                                             :Pages
                                             [:ISBN (su/format-isbn (value-of pub-ref "ISBN"))]
                                             [:DOI (when-let [doi (value-of pub-ref "DOI")]
                                                     {:DOI doi})]
                                             [:OnlineResource (dif-util/parse-publication-reference-online-resouce pub-ref sanitize?)]
                                             :Other_Reference_Details])))
     :AncillaryKeywords (values-at doc "/DIF/Keyword")
     :ScienceKeywords (for [sk (select doc "/DIF/Parameters")]
                        {:Category (value-of sk "Category")
                         :Topic (value-of sk "Topic")
                         :Term (value-of sk "Term")
                         :VariableLevel1 (value-of sk "Variable_Level_1")
                         :VariableLevel2 (value-of sk "Variable_Level_2")
                         :VariableLevel3 (value-of sk "Variable_Level_3")
                         :DetailedVariable (value-of sk "Detailed_Variable")})
     :RelatedUrls (parse-related-urls doc sanitize?)
     :MetadataAssociations (for [parent-dif (values-at doc "/DIF/Parent_DIF")]
                             {:EntryId parent-dif})
     :ContactPersons (contact/parse-contact-persons (select doc "/DIF/Personnel"))
     :DataCenters (concat (center/parse-data-centers doc sanitize?)
                          (center/parse-processing-centers doc))
     :MetadataSpecification (umm-coll-models/map->MetadataSpecificationType
                             {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                        umm-spec-versioning/current-collection-version),
                              :Name "UMM-C"
                              :Version umm-spec-versioning/current-collection-version})}))

(defn dif9-xml-to-umm-c
  "Returns UMM-C collection record from DIF9 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [metadata options]
  (js/parse-umm-c (parse-dif9-xml metadata options)))
