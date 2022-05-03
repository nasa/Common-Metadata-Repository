(ns cmr.system-int-test.data2.umm-spec-subscription
  "Contains subscription data generators for example-based testing in system
  integration tests."
  (:require
   [cmr.common.mime-types :as mime-types]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.umm-spec.models.umm-subscription-models :as umm-sub]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm-spec.versioning :as umm-versioning]))

(def context (lkt/setup-context-for-test))

(def ^:private latest-umm-sub-verison umm-versioning/current-subscription-version)

(defn- sample-umm-subscription
  [version]
  (get {"1.0" {:Name "someSub"
               :SubscriberId "someSubId"
               :CollectionConceptId "C123-PROV1"
               :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"}
        "1.1" {:Name "someSub"
               :Type "granule"
               :SubscriberId "someSubId"
               :CollectionConceptId "C123-PROV1"
               :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"
               :MetadataSpecification
               {:URL "https://cdn.earthdata.nasa.gov/umm/subscription/v1.1"
                :Name "UMM-Sub"
                :Version "1.1"}}}
       version))


(defn- subscription
  "Returns a UMM-Sub record from the given attribute map."
  ([]
   (subscription {}))
  ([attribs]
   (subscription attribs latest-umm-sub-verison))
  ([attribs version]
   (let [sub-attrs (merge (sample-umm-subscription version) attribs)
         sub-attrs (if (= "collection" (:Type sub-attrs))
                     (dissoc sub-attrs :CollectionConceptId)
                     sub-attrs)]
     (umm-sub/map->UMM-Sub sub-attrs))))

(defn- umm-sub->concept
  "Returns a concept map from a UMM subscription item or tombstone."
  [item]
  (let [format-key :umm-json
        format (mime-types/format->mime-type format-key)]
    (merge {:concept-type :subscription
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

(defn subscription-concept
  "Returns the subscription for ingest with the given attributes"
  ([attribs]
   (subscription-concept attribs latest-umm-sub-verison))
  ([attribs umm-sub-version]
   (let [{:keys [provider-id native-id]} attribs]
     (-> (subscription attribs umm-sub-version)
         (assoc :provider-id provider-id :native-id native-id)
         umm-sub->concept))))
