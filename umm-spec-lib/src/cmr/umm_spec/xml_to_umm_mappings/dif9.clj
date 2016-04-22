(ns cmr.umm-spec.xml-to-umm-mappings.dif9
  "Defines mappings from DIF9 XML into UMM records"
  (:require [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.xml.parse :refer :all]
            [camel-snake-kebab.core :as csk]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm.dif.date-util :refer [parse-dif-end-date]]))

(def dif-iso-topic-category->umm-iso-topic-category
  "DIF ISOTopicCategory to UMM ISOTopicCategory mapping. Some of the DIF ISOTopicCategory are made
  up based on intuition and may not be correct. Fix them when identified."
  {"CLIMATOLOGY/METEOROLOGY/ATMOSPHERE" "climatologyMeteorologyAtmosphere"
   "ENVIRONMENT" "environment"
   "FARMING" "farming"
   "BIOTA" "biota"
   "BOUNDARIES" "boundaries"
   "ECONOMY" "economy"
   "ELEVATION" "elevation"
   "GEOSCIENTIFIC/INFORMATION" "geoscientificInformation"
   "HEALTH" "health"
   "IMAGERY/BASE MAPS/EARTH COVER" "imageryBaseMapsEarthCover"
   "INTELLIGENCE/MILITARY" "intelligenceMilitary"
   "INLAND/WATERS" "inlandWaters"
   "LOCATION" "location"
   "OCEANS" "oceans"
   "PLANNING/CADASTRE" "planningCadastre"
   "SOCIETY" "society"
   "STRUCTURE" "structure"
   "TRANSPORTATION" "transportation"
   "UTILITIES/COMMUNICATION" "utilitiesCommunication"})

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
  [doc]
  (let [platforms (parse-just-platforms doc)
        instruments (parse-instruments doc)]
    ;; When there is only one platform in the collection, associate the instruments on that platform.
    ;; Otherwise, create a dummy platform to hold all instruments and add that to the platforms.
    (if (= 1 (count platforms))
      (map #(assoc % :Instruments instruments) platforms)
      (if instruments
        (conj platforms {:ShortName su/not-provided
                         :LongName su/not-provided
                         :Instruments instruments})
        platforms))))

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

(defn- parse-dif9-xml
  "Returns collection map from DIF9 collection XML document."
  [doc]
  (let [entry-id (value-of doc "/DIF/Entry_ID")
        version-id (value-of doc "/DIF/Data_Set_Citation/Version")
        short-name (get-short-name entry-id version-id)]
    {:EntryTitle (value-of doc "/DIF/Entry_Title")
     :ShortName short-name
     :Version (or version-id su/not-provided)
     :Abstract (value-of doc "/DIF/Summary/Abstract")
     :CollectionDataType (value-of doc "/DIF/Extended_Metadata/Metadata[Name='CollectionDataType']/Value")
     :Purpose (value-of doc "/DIF/Summary/Purpose")
     :DataLanguage (value-of doc "/DIF/Data_Set_Language")
     :ISOTopicCategories (values-at doc "DIF/ISO_Topic_Category")
     :TemporalKeywords (values-at doc "/DIF/Data_Resolution/Temporal_Resolution")
     :Projects (for [proj (select doc "/DIF/Project")]
                 {:ShortName (value-of proj "Short_Name")
                  :LongName (value-of proj "Long_Name")})
     :CollectionProgress (value-of doc "/DIF/Data_Set_Progress")
     :SpatialKeywords (values-at doc "/DIF/Location/Location_Category")
     :LocationKeywords  (for [lk (select doc "/DIF/Location")]
                        {:Category (value-of lk "Location_Category")
                         :Type (value-of lk "Location_Type")
                         :Location_Subregion1 (value-of lk "Location_Subregion1")
                         :Location_Subregion2 (value-of lk "Location_Subregion2")
                         :Location_Subregion3 (value-of lk "Location_Subregion3")
                         :DetailedLocation (value-of lk "Detailed_Location")})
     :Quality (value-of doc "/DIF/Quality")
     :AccessConstraints {:Description (value-of doc "/DIF/Access_Constraints")
                         :Value (value-of doc "/DIF/Extended_Metadata/Metadata[Name='Restriction']/Value")}
     :UseConstraints (value-of doc "/DIF/Use_Constraints")
     :Platforms (parse-platforms doc)
     :TemporalExtents (when-let [temporals (select doc "/DIF/Temporal_Coverage")]
                        [{:RangeDateTimes (for [temporal temporals]
                                            {:BeginningDateTime (value-of temporal "Start_Date")
                                             :EndingDateTime (parse-dif-end-date (value-of temporal "Stop_Date"))})}])
     :SpatialExtent {:HorizontalSpatialDomain {:Geometry {:BoundingRectangles (parse-mbrs doc)}}}
     :Distributions (for [distribution (select doc "/DIF/:Distribution")]
                      {:DistributionMedia (value-of distribution "Distribution_Media")
                       :Sizes (su/parse-data-sizes (value-of distribution "Distribution_Size"))
                       :DistributionFormat (value-of distribution "Distribution_Format")
                       :Fees (value-of distribution "Fees")})
     :ProcessingLevel {:Id
                       (value-of doc "/DIF/Extended_Metadata/Metadata[Name='ProcessingLevelId']/Value")

                       :ProcessingLevelDescription
                       (value-of doc "/DIF/Extended_Metadata/Metadata[Name='ProcessingLevelDescription']/Value")}

     :AdditionalAttributes (for [aa (select doc "/DIF/Extended_Metadata/Metadata[Group='AdditionalAttribute']")]
                             {:Name (value-of aa "Name")
                              :Description (value-of aa "Description")
                              :DataType (value-of aa "Type")
                              :Group "AdditionalAttribute"
                              :ParameterRangeBegin (value-of aa "Value[@type='ParamRangeBegin']")
                              :ParameterRangeEnd (value-of aa "Value[@type='ParamRangeEnd']")
                              :Value (value-of aa "Value[@type='Value']")
                              :MeasurementResolution (value-of aa "Value[@type='MeasurementResolution']")
                              :ParameterUnitsOfMeasure (value-of aa "Value[@type='ParameterUnitsOfMeasure']")
                              :ParameterValueAccuracy (value-of aa "Value[@type='ParameterValueAccuracy']")
                              :ValueAccuracyExplanation (value-of aa "Value[@type='ValueAccuracyExplanation']")
                              :UpdateDate (value-of aa "Value[@type='UpdateDate']")})
     :PublicationReferences (for [pub-ref (select doc "/DIF/Reference")]
                              (into {} (map (fn [x]
                                              (if (keyword? x)
                                                [(csk/->PascalCaseKeyword x) (value-of pub-ref (str x))]
                                                x))
                                            [:Author
                                             :Publication_Date
                                             :Title
                                             :Series
                                             :Edition
                                             :Volume
                                             :Issue
                                             :Report_Number
                                             :Publication_Place
                                             :Publisher
                                             :Pages
                                             [:ISBN (value-of pub-ref "ISBN")]
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
     :RelatedUrls (for [related-url (select doc "/DIF/Related_URL")
                        :let [description (value-of related-url "Description")]]
                    {:URLs (values-at related-url "URL")
                     :Description description
                     :Relation [(value-of related-url "URL_Content_Type/Type")
                                (value-of related-url "URL_Content_Type/Subtype")]})
     :MetadataAssociations (for [parent-dif (values-at doc "/DIF/Parent_DIF")]
                             {:EntryId parent-dif})}))

(defn dif9-xml-to-umm-c
  "Returns UMM-C collection record from DIF9 collection XML document."
  [metadata]
  (js/parse-umm-c (parse-dif9-xml metadata)))
