(ns cmr.ingest.services.ingest-service.granule
  (:require
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.ingest.services.helper :as h]
   [cmr.ingest.services.ingest-service.collection :as collection]
   [cmr.ingest.services.messages :as msg]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn- validate-granule-collection-ref
  "Throws bad request exception when collection-ref is missing required fields."
  [collection-ref]
  (let [{:keys [short-name version-id entry-title entry-id]} collection-ref]
    (when-not (or entry-title entry-id (and short-name version-id))
      (errors/throw-service-error
        :invalid-data
        "Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."))))

(defn-timed get-granule-parent-collection-and-concept
  "Returns the parent collection concept, parsed UMM spec record, and the parse UMM lib record for a
  granule as a tuple. Finds the parent collection using the provider id and collection ref. This will
  correctly handle situations where there might be multiple concept ids that used a short name and
  version id or entry title but were previously deleted."
  [context concept granule]
  (validate-granule-collection-ref (:collection-ref granule))
  (let [provider-id (:provider-id concept)
        {:keys [granule-ur collection-ref]} granule
        params (util/remove-nil-keys (merge {:provider-id provider-id}
                                            collection-ref))
        coll-concept (first (h/find-visible-collections context params))]
    (when-not coll-concept
      (cmsg/data-error :invalid-data
                       msg/parent-collection-does-not-exist provider-id granule-ur collection-ref))
    [coll-concept
     (spec/parse-metadata
      context :collection (:format coll-concept) (:metadata coll-concept))]))

(defn- add-extra-fields-for-granule
  "Adds the extra fields for a granule concept."
  [context concept granule collection-concept]
  (let [{:keys [granule-ur]
         {:keys [delete-time]} :data-provider-timestamps} granule
        parent-collection-id (:concept-id collection-concept)
        parent-entry-title (get-in collection-concept [:extra-fields :entry-title])]
    (assoc concept :extra-fields {:parent-collection-id parent-collection-id
                                  :parent-entry-title parent-entry-title
                                  :delete-time (when delete-time (str delete-time))
                                  :granule-ur granule-ur})))

(defn-timed validate-granule
  "Validate a granule concept. Throws a service error if any validation issues are found.
  Returns a tuple of the parent collection concept and the granule concept.

  Accepts an optional function for looking up the parent collection concept and UMM record as a tuple.
  This can be used to provide the collection through an alternative means like the API."
  ([context concept]
   (validate-granule
    context concept get-granule-parent-collection-and-concept))
  ([context concept fetch-parent-collection-concept-fn]
   (v/validate-concept-request concept)
   (v/validate-concept-metadata concept)

   (let [granule (umm-legacy/parse-concept context concept)
         [parent-collection-concept
          umm-spec-collection](fetch-parent-collection-concept-fn
                               context concept granule)]
     ;; UMM Validation
     (v/validate-granule-umm-spec context umm-spec-collection granule)
     
     ;; Add extra fields for the granule
     (let [gran-concept (add-extra-fields-for-granule
                         context concept granule parent-collection-concept)]
       (v/validate-business-rules context gran-concept)
       [parent-collection-concept gran-concept]))))

(defn validate-granule-with-parent-collection
  "Validate a granule concept along with a parent collection. Throws a service error if any
  validation issues are found."
  [context concept parent-collection-concept]
  (let [collection (:collection
                    (errors/handle-service-errors
                     #(collection/validate-and-parse-collection-concept
                       context parent-collection-concept false)
                     (fn [type errors ex]
                       (errors/throw-service-errors
                         type (map msg/invalid-parent-collection-for-validation errors)) ex)))]
    (validate-granule context concept
                      (constantly [parent-collection-concept
                                   collection]))))

(defn-timed save-granule
  "Store a concept in mdb and indexer and return concept-id and revision-id."
  [context concept]
  (let [[coll-concept concept] (validate-granule context concept)
        {:keys [concept-id revision-id]} (mdb/save-concept context concept)]
    {:concept-id concept-id, :revision-id revision-id}))
