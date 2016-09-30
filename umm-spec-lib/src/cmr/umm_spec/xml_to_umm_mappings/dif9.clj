(ns cmr.umm-spec.xml-to-umm-mappings.dif9
  "Defines mappings from DIF9 XML into UMM records"
  (:require
    [camel-snake-kebab.core :as csk]
    [clj-time.format :as f]
    [cmr.common.xml.simple-xpath :refer [select text]]
    [cmr.common.xml.parse :refer :all]
    [cmr.umm.dif.date-util :refer [parse-dif-end-date]]
    [cmr.umm-spec.date-util :as date]
    [cmr.umm-spec.dif-util :as dif-util]
    [cmr.umm-spec.json-schema :as js]
    [cmr.umm-spec.models.umm-common-models :as cmn]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.additional-attribute :as aa]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.data-center :as center]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.data-contact :as contact]
    [cmr.umm-spec.xml-to-umm-mappings.dif9.paleo-temporal :as pt]
    [cmr.umm-spec.util :as su]
    [cmr.umm-spec.url :as url]))

(defn- parse-mbrs
  "Returns a seq of bounding rectangle maps in the given DIF XML doc."
  [doc]
  (for [el (select doc "/DIF/Spatial_Coverage")]
    {:NorthBoundingCoordinate (value-of el "Northernmost_Latitude")
     :SouthBoundingCoordinate (value-of el "Southernmost_Latitude")
     :WestBoundingCoordinate (value-of el "Westernmost_Longitude")
     :EastBoundingCoordinate (value-of el "Easternmost_Longitude")}))

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
  "Returns a list of related urls"
  [doc sanitize?]
  (if-let [related-urls (seq (select doc "/DIF/Related_URL"))]
    (for [related-url related-urls
          :let [description (value-of related-url "Description")]]
      {:URLs (map #(url/format-url % sanitize?) (values-at related-url "URL"))
       :Description description
       :Relation [(value-of related-url "URL_Content_Type/Type")
                  (value-of related-url "URL_Content_Type/Subtype")]})
    (when sanitize?
      [su/not-provided-related-url])))

(defn- parse-dif9-xml
  "Returns collection map from DIF9 collection XML document."
  [doc {:keys [sanitize?]}]
  (let [entry-id (value-of doc "/DIF/Entry_ID")
        version-id (value-of doc "/DIF/Data_Set_Citation/Version")
        short-name (get-short-name entry-id version-id)]
    {:EntryTitle (value-of doc "/DIF/Entry_Title")
     :ShortName (su/truncate-with-default short-name su/SHORTNAME_MAX sanitize?)
     :Version (or version-id (when sanitize? su/not-provided))
     :Abstract (su/truncate-with-default (value-of doc "/DIF/Summary/Abstract") su/ABSTRACT_MAX sanitize?)
     :CollectionDataType (value-of doc "/DIF/Extended_Metadata/Metadata[Name='CollectionDataType']/Value")
     :Purpose (su/truncate (value-of doc "/DIF/Summary/Purpose") su/PURPOSE_MAX sanitize?)
     :DataLanguage (dif-util/dif-language->umm-langage (value-of doc "/DIF/Data_Set_Language"))
     :MetadataDates (parse-metadata-dates doc)
     :ISOTopicCategories (dif-util/parse-iso-topic-categories doc sanitize?)
     :TemporalKeywords (values-at doc "/DIF/Data_Resolution/Temporal_Resolution")
     :Projects (for [proj (select doc "/DIF/Project")]
                 {:ShortName (value-of proj "Short_Name")
                  :LongName (su/truncate (value-of proj "Long_Name") su/PROJECT_LONGNAME_MAX sanitize?)})
     :CollectionProgress (value-of doc "/DIF/Data_Set_Progress")
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
     :UseConstraints (su/truncate (value-of doc "/DIF/Use_Constraints") su/USECONSTRAINTS_MAX sanitize?)
     :Platforms (parse-platforms doc sanitize?)
     :TemporalExtents (if-let [temporals (select doc "/DIF/Temporal_Coverage")]
                        [{:RangeDateTimes (for [temporal temporals]
                                            {:BeginningDateTime (date/with-default (value-of temporal "Start_Date") sanitize?)
                                             :EndingDateTime (parse-dif-end-date (value-of temporal "Stop_Date"))})}]
                        (when sanitize? su/not-provided-temporal-extents))
     :PaleoTemporalCoverages (pt/parse-paleo-temporal doc)
     :SpatialExtent (merge {:GranuleSpatialRepresentation (or (value-of doc "/DIF/Extended_Metadata/Metadata[Name='GranuleSpatialRepresentation']/Value")
                                                              (when sanitize?
                                                                "NO_SPATIAL"))}
                           (when-let [brs (seq (parse-mbrs doc))]
                             {:SpatialCoverageType "HORIZONTAL"
                              :HorizontalSpatialDomain
                              {:Geometry {:CoordinateSystem "CARTESIAN" ;; DIF9 doesn't have CoordinateSystem, default to CARTESIAN
                                          :BoundingRectangles brs}}}))
     :Distributions (for [distribution (select doc "/DIF/:Distribution")]
                      {:DistributionMedia (value-of distribution "Distribution_Media")
                       :Sizes (su/parse-data-sizes (value-of distribution "Distribution_Size"))
                       :DistributionFormat (value-of distribution "Distribution_Format")
                       :Fees (value-of distribution "Fees")})
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
                                             [:DOI {:DOI (value-of pub-ref "DOI")}]
                                             [:RelatedUrl
                                              {:URLs (seq
                                                       (remove nil? [(value-of pub-ref "Online_Resource")]))}]
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
     :DataCenters (concat (center/parse-originating-centers doc)
                          (center/parse-data-centers doc sanitize?)
                          (center/parse-processing-centers doc))}))

(defn dif9-xml-to-umm-c
  "Returns UMM-C collection record from DIF9 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [metadata options]
  (js/parse-umm-c (parse-dif9-xml metadata options)))
