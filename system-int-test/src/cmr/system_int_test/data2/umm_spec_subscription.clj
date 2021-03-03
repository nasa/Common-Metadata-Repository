(ns cmr.system-int-test.data2.umm-spec-subscription
  "Contains subscription data generators for example-based testing in system
  integration tests."
  (:require
   [cmr.common.mime-types :as mime-types]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.umm-spec.models.umm-subscription-models :as umm-sub]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(def context (lkt/setup-context-for-test))

(def ^:private sample-umm-subscription
  {:Name "someSub"
   :SubscriberId "someSubId"
   :CollectionConceptId "C123-PROV1"
   :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"})

(defn- subscription
  "Returns a UMM-Sub record from the given attribute map."
  ([]
   (subscription {}))
  ([attribs]
   (umm-sub/map->UMM-Sub (merge sample-umm-subscription attribs)))
  ([index attribs]
   (umm-sub/map->UMM-Sub
    (merge sample-umm-subscription
           {:Name (str "Name " index)}
           attribs))))

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
   (let [{:keys [provider-id native-id]} attribs]
     (-> (subscription attribs)
         (assoc :provider-id provider-id :native-id native-id)
         umm-sub->concept)))
  ([attribs index]
   (let [{:keys [provider-id native-id]} attribs]
     (-> index
         (subscription attribs)
         (assoc :provider-id provider-id :native-id native-id)
         umm-sub->concept))))
