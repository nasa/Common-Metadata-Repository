(ns cmr.system-int-test.data2.umm-spec-service
  "Contains service data generators for example-based testing in system
  integration tests."
  (:require
   [cmr.common.mime-types :as mime-types]
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
   :RelatedURL {
     :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
     :Description "OPeNDAP Service"
     :Type "GET SERVICE"
     :URLContentType "CollectionURL"}
   :ServiceKeywords [
      {:ServiceCategory "DATA ANALYSIS AND VISUALIZATION"
       :ServiceTopic "VISUALIZATION/IMAGE PROCESSING"}]
   :ServiceOrganizations [
      {:Roles ["SERVICE PROVIDER"]
       :ShortName "LDPAAC"}]})

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
