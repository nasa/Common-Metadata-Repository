(ns cmr.system-int-test.data2.umm-spec-tool
  "Contains tool data generators for example-based testing in system
  integration tests."
  (:require
   [cmr.common.mime-types :as mime-types]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.umm-spec.models.umm-tool-models :as umm-t]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(def context (lkt/setup-context-for-test))

(def ^:private sample-umm-tool
  {:Name "USGS_TOOLS_LATLONG"
   :LongName "WRS-2 Path/Row to Latitude/Longitude Converter"
   :Type "Downloadable Tool"
   :Version "1.0"
   :Description "The USGS WRS-2 Path/Row to Latitude/Longitude Converter allows users to enter any Landsat path and row to get the nearest scene center latitude and longitude coordinates."
   :URL {:URLContentType "DistributionURL"
         :Type "DOWNLOAD SOFTWARE"
         :Description "Access the WRS-2 Path/Row to Latitude/Longitude Converter."
         :URLValue "http://www.scp.byu.edu/software/slice_response/Xshape_temp.html"}
   :ToolKeywords [{:ToolCategory "EARTH SCIENCE SERVICES"
                   :ToolTopic "DATA MANAGEMENT/DATA HANDLING"
                   :ToolTerm "DATA INTEROPERABILITY"
                   :ToolSpecificTerm "DATA REFORMATTING"}]
   :Organizations [{:Roles ["SERVICE PROVIDER"]
                    :ShortName "USGS/EROS"
                    :LongName "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES"
                    :URLValue "http://www.usgs.gov"}]
   :PotentialAction {:Type "SearchAction"
                     :Target {:Type "EntryPoint",
                              :UrlTemplate "https://podaac-tools.jpl.nasa.gov/soto/#b=BlueMarble_ShadedRelief_Bathymetry&l={layers}&ve={bbox}&d={date}"
                              :HttpMethod [ "GET" ] }
                     :QueryInput [{:ValueName "layers"
                                   :Description "A comma-separated list of visualization layer ids, as defined by GIBS. These layers will be portrayed on the web application"
                                   :ValueRequired true
                                   :ValueType "https://wiki.earthdata.nasa.gov/display/GIBS/GIBS+API+for+Developers#GIBSAPIforDevelopers-LayerNaming"}
                                  {:ValueName "date"
                                   :ValueType "https://schema.org/startDate"}
                                  {:ValueName "bbox"
                                   :ValueType "https://schema.org/box"}]}
   :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.2.0"
                           :Name "UMM-T"
                           :Version "1.2.0"}})

(defn- tool
  "Returns a UMM-T record from the given attribute map."
  ([]
   (tool {}))
  ([attribs]
   (umm-t/map->UMM-T (merge sample-umm-tool attribs)))
  ([index attribs]
   (umm-t/map->UMM-T
    (merge sample-umm-tool
           {:Name (str "Name " index)}
           attribs))))

(defn- umm-t->concept
  "Returns a concept map from a UMM tool item or tombstone."
  [item]
  (let [format-key :umm-json
        format (mime-types/format->mime-type format-key)]
    (merge {:concept-type :tool
            :provider-id (or (:provider-id item) "PROV1")
            :native-id (or (:native-id item) (:Name item))
            :metadata (when-not (:deleted item)
                        (umm-spec/generate-metadata
                         context
                         (dissoc item :provider-id :concept-id :native-id)
                         format-key))
            :format format}
           (when (:concept-id item)
             {:concept-id (:concept-id item)})
           (when (:revision-id item)
             {:revision-id (:revision-id item)}))))

(defn tool-concept
  "Returns the tool for ingest with the given attributes"
  ([attribs]
   (let [{:keys [provider-id native-id]} attribs]
     (-> (tool attribs)
         (assoc :provider-id provider-id :native-id native-id)
         umm-t->concept)))
  ([attribs index]
   (let [{:keys [provider-id native-id]} attribs]
     (-> index
         (tool attribs)
         (assoc :provider-id provider-id :native-id native-id)
         umm-t->concept))))

(defn contact-person
  "Returns a ContactPersonType suitable as an element in a
  Persons collection."
  ([]
   (contact-person {}))
  ([attribs]
   (umm-t/map->ContactPersonType
     (merge {:FirstName "Alice"
             :LastName "Bob"
             :Roles ["AUTHOR"]}
            attribs))))

(defn contact-mechanism
  "Returns a ContactMechanismType suitable as an element in a
  ContactMechanisms collection."
  ([]
   (contact-mechanism {}))
  ([attribs]
   (umm-t/map->ContactMechanismType
     (merge {:Type "Email"
             :Value "alice@example.com"}
            attribs))))

(defn address
  "Returns a AddressType suitable as an element in an Addresses
  collection."
  ([]
    (address {}))
  ([attribs]
   (umm-t/map->AddressType
     (merge {:StreetAddresses ["101 S. Main St"]
             :City "Anytown"
             :StateProvince "ST"}
            attribs))))

(defn contact-info
  "Returns a ContactInformationType suitable as an element in a
  ContactInformation collection."
  ([]
   (contact-info {}))
  ([attribs]
   (umm-t/map->ContactInformationType
     (merge {:ContactMechanisms [(contact-mechanism)]
             :Addresses [(address)]}
            attribs))))

(defn contact-group
  "Returns a ContectGroupType suitable for inclusion in ContactGroups."
  ([]
    (contact-group {}))
  ([attribs]
   (umm-t/map->ContactGroupType
    (merge {:Roles ["SERVICE PROVIDER" "AUTHOR"]
            :ContactInformation (contact-info)
            :GroupName "Contact Group Name"}
            attribs))))

(defn tool-keywords
  "Returns a ToolKeywordType suitable as an element in a
  ToolKeywords collection."
  ([]
    (tool-keywords {}))
  ([attribs]
   (umm-t/map->ToolKeywordType
     (merge {:ToolCategory "A tool category"
             :ToolTopic "A tool topic"
             :ToolTerm "A tool term"
             :ToolSpecificTerm "A specific tool term"}
            attribs))))

(defn organization
  "Returns a OrganizationType suitable as an element in a
  Organizations collection."
  ([]
    (organization {}))
  ([attribs]
   (umm-t/map->OrganizationType
     (merge {:Roles ["SERVICE PROVIDER" "PUBLISHER"]
             :ShortName "tlorg1"
             :LongName "Tool Org 1"
             :URLValue "https://lpdaac.usgs.gov/"}
            attribs))))

(defn url
  "Returns a url suitable as an element."
  ([]
    (url {}))
  ([attribs]
   (umm-t/map->URLType
     (merge {:URLValue "http://example.com/file"
             :Description "description"
             :Type "GET SERVICE"
             :Subtype "SUBSETTER"
             :URLContentType "CollectionURL"}
            attribs))))

(defn related-url
  "Creates related url for online_only test"
  ([]
   (related-url {}))
  ([attribs]
   (umm-t/map->RelatedURLType (merge {:URL "http://example.com/file"
                                      :Description "description"
                                      :Type "GET SERVICE"
                                      :URLContentType "CollectionURL"}
                                     attribs))))
