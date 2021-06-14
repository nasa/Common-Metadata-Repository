(ns cmr.system-int-test.data2.umm-spec-service
  "Contains service data generators for example-based testing in system
  integration tests."
  (:require
   [cmr.common.mime-types :as mime-types]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.models.umm-service-models :as umm-s]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(def context (lkt/setup-context-for-test))

(def ^:private sample-umm-service
  {:Name "AIRX3STD"
   :LongName "OPeNDAP Service for AIRS Level-3 retrieval products"
   :Type "OPeNDAP"
   :Version "1.9"
   :Description "AIRS Level-3 retrieval product created using AIRS IR, AMSU without HSB."
   :URL {:URLValue "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
         :Description "OPeNDAP Service"}
   :ServiceKeywords [
      {:ServiceCategory "DATA ANALYSIS AND VISUALIZATION"
       :ServiceTopic "VISUALIZATION/IMAGE PROCESSING"}]
   :ServiceOrganizations [
      {:Roles ["SERVICE PROVIDER"]
       :ShortName "LDPAAC"}]
   :MetadataSpecification (m-spec/metadata-spec-content :service)})

(defn- service
  "Returns a UMM-S record from the given attribute map."
  ([]
   (service {}))
  ([attribs]
   (umm-s/map->UMM-S (merge sample-umm-service attribs)))
  ([index attribs]
   (umm-s/map->UMM-S
    (merge sample-umm-service
           {:Name (str "Name " index)}
           attribs))))

(defn- umm-s->concept
  "Returns a concept map from a UMM service item or tombstone."
  [item]
  (let [format-key :umm-json
        format (mime-types/format->mime-type format-key)]
    (merge {:concept-type :service
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

(defn service-concept
  "Returns the service for ingest with the given attributes"
  ([attribs]
   (let [{:keys [provider-id native-id]} attribs]
     (-> (service attribs)
         (assoc :provider-id provider-id :native-id native-id)
         umm-s->concept)))
  ([attribs index]
   (let [{:keys [provider-id native-id]} attribs]
     (-> index
         (service attribs)
         (assoc :provider-id provider-id :native-id native-id)
         umm-s->concept))))

(defn contact-person
  "Returns a ContactPersonType suitable as an element in a
  Persons collection."
  ([]
   (contact-person {}))
  ([attribs]
   (umm-s/map->ContactPersonType
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
   (umm-s/map->ContactMechanismType
     (merge {:Type "Email"
             :Value "alice@example.com"}
            attribs))))

(defn address
  "Returns a AddressType suitable as an element in an Addresses
  collection."
  ([]
    (address {}))
  ([attribs]
   (umm-s/map->AddressType
     (merge {:StreetAddresses ["101 S. Main St"]
             :City "Anytown"
             :StateProvince "ST"}
            attribs))))

(defn online-resource
  "Returns an online-resource suitable as an element."
  ([]
    (online-resource {}))
  ([attribs]
   (umm-s/map->OnlineResourceType
     (merge {:Linkage "http://example.com/file"
             :Description "description"
             :Name "PROJECT HOME PAGE"}
            attribs))))

(defn contact-info
  "Returns a ContactInformationType suitable as an element in a
  ContactInformation collection."
  ([]
   (contact-info {}))
  ([attribs]
   (umm-s/map->ContactInformationType
     (merge {:OnlineResources [(online-resource)]
             :ContactMechanisms [(contact-mechanism)]
             :Addresses [(address)]}
            attribs))))

(defn contact-group
  "Returns a ContectGroupType suitable for inclusion in ContactGroups."
  ([]
    (contact-group {}))
  ([attribs]
   (umm-s/map->ContactGroupType
    (merge {:Roles ["SERVICE PROVIDER CONTACT" "TECHNICAL CONTACT"]
            :ContactInformation (contact-info)
            :GroupName "Contact Group Name"}
           attribs))))

(defn service-keywords
  "Returns a ServiceKeywordType suitable as an element in a
  ServiceKeywords collection."
  ([]
    (service-keywords {}))
  ([attribs]
   (umm-s/map->ServiceKeywordType
     (merge {:ServiceCategory "A service category"
             :ServiceTopic "A service topic"
             :ServiceTerm "A service term"
             :ServiceSpecificTerm "A specific service term"}
            attribs))))

(defn service-organization
  "Returns a ServiceOrganizationType suitable as an element in a
  ServiceOrganizations collection."
  ([]
    (service-organization {}))
  ([attribs]
   (umm-s/map->ServiceOrganizationType
     (merge {:Roles ["SERVICE PROVIDER" "PUBLISHER"]
             :ShortName "svcorg1"
             :LongName "Service Org 1"
             :OnlineResource (online-resource)}
            attribs))))

(defn url
  "Returns a url suitable as an element."
  ([]
    (url {}))
  ([attribs]
   (umm-s/map->URLType
     (merge {:URLValue "http://example.com/file"
             :Description "description"}
            attribs))))
