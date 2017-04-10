(ns cmr.ingest.services.ingest-service
  (:require
    [clojure.string :as str]
    [cmr.common.cache :as cache]
    [cmr.common.config :as cfg :refer [defconfig]]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.services.messages :as cmsg]
    [cmr.common.util :as util :refer [defn-timed]]
    [cmr.ingest.config :as config]
    [cmr.ingest.data.ingest-events :as ingest-events]
    [cmr.ingest.data.provider-acl-hash :as pah]
    [cmr.ingest.services.helper :as h]
    [cmr.ingest.services.messages :as msg]
    [cmr.ingest.validation.validation :as v]
    [cmr.message-queue.services.queue :as queue]
    [cmr.oracle.connection :as conn]
    [cmr.transmit.cubby :as cubby]
    [cmr.transmit.echo.rest :as rest]
    [cmr.transmit.indexer :as indexer]
    [cmr.transmit.metadata-db :as mdb]
    [cmr.transmit.metadata-db2 :as mdb2]
    [cmr.umm-spec.legacy :as umm-legacy]
    [cmr.umm-spec.umm-spec-core :as spec]
    [cmr.umm-spec.versioning :as ver]
    [cmr.umm.collection.entry-id :as eid]))

(defn add-extra-fields-for-collection
  "Returns collection concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept collection]
  (let [{short-name :ShortName
         version-id :Version
         entry-title :EntryTitle} collection
        entry-id (eid/entry-id short-name version-id)
        delete-time (first (map :Date (filter #(= "DELETE" (:Type %)) (:DataDates collection))))]
    (assoc concept :extra-fields {:entry-title entry-title
                                  :entry-id entry-id
                                  :short-name short-name
                                  :version-id version-id
                                  :delete-time (when delete-time (str delete-time))})))

(defn- validate-and-parse-collection-concept
  "Validates a collection concept and parses it. Returns the UMM record and any warnings from
  validation."
  [context collection-concept validation-options]
  (v/validate-concept-request collection-concept)
  (v/validate-concept-metadata collection-concept)
  (let [{:keys [format metadata]} collection-concept
        collection (spec/parse-metadata context :collection format metadata {:sanitize? false})
        sanitized-collection (spec/parse-metadata context :collection format metadata)
        _ (v/umm-spec-validate-collection collection validation-options context)
        ;; Return warnings for schema validation errors going from xml -> UMM
        warnings (v/validate-collection-umm-spec-schema collection validation-options)
        collection-warnings (v/umm-spec-validate-collection-warnings
                             collection validation-options context)
        collection-warnings (map #(str (:path %) " " (str/join " " (:errors %)))
                                 collection-warnings)
        warnings (concat warnings collection-warnings)]
    ;; The sanitized UMM Spec collection is returned so that ingest does not fail
    {:collection sanitized-collection
     :warnings warnings}))

(defn- fix-ingest-concept-format
   "Fixes formats"
  [fmt]
  (if (or
        (not (mt/umm-json? fmt))
        (mt/version-of fmt))
    fmt
    (str fmt ";version=" (config/ingest-accept-umm-version))))

(defn-timed validate-and-prepare-collection
  "Validates the collection and adds extra fields needed for metadata db. Throws a service error
  if any validation issues are found and errors are enabled, otherwise returns errors as warnings."
  [context concept validation-options]
  (let [concept (update-in concept [:format] fix-ingest-concept-format)
        {:keys [collection warnings]} (validate-and-parse-collection-concept context
                                                                             concept
                                                                             validation-options)
        ;; Add extra fields for the collection
        coll-concept (add-extra-fields-for-collection context concept collection)]
    ;; Validate ingest business rules through umm-spec-lib
    (v/validate-business-rules
     context (assoc coll-concept :umm-concept collection))
    {:concept coll-concept
     :warnings warnings}))

(defn- validate-granule-collection-ref
  "Throws bad request exception when collection-ref is missing required fields."
  [collection-ref]
  (let [{:keys [short-name version-id entry-title entry-id]} collection-ref]
    (when-not (or entry-title entry-id (and short-name version-id))
      (errors/throw-service-error
        :invalid-data
        "Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."))))

(defn- get-granule-parent-collection-and-concept
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
                     #(validate-and-parse-collection-concept context parent-collection-concept false)
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

(defn-timed save-collection
  "Store a concept in mdb and indexer.
   Return entry-titile, concept-id, revision-id, and warnings."
  [context concept validation-options]
  (let [{:keys [concept warnings]} (validate-and-prepare-collection context
                                                                    concept
                                                                    validation-options)]
    (let [{:keys [concept-id revision-id]} (mdb/save-concept context concept)
          entry-title (get-in concept [:extra-fields :entry-title])]
      {:entry-title entry-title
       :concept-id concept-id
       :revision-id revision-id
       :warnings warnings})))

(defn-timed delete-concept
  "Delete a concept from mdb and indexer. Throws a 404 error if the concept does not exist or
  the latest revision for the concept is already a tombstone."
  [context concept-attribs]
  (let [{:keys [concept-type provider-id native-id]} concept-attribs
        existing-concept (first (mdb/find-concepts context
                                                   {:provider-id provider-id
                                                    :native-id native-id
                                                    :exclude-metadata true
                                                    :latest true}
                                                   concept-type))
        concept-id (:concept-id existing-concept)]
    (when-not concept-id
      (errors/throw-service-error
        :not-found (cmsg/invalid-native-id-msg concept-type provider-id native-id)))
    (when (:deleted existing-concept)
      (errors/throw-service-error
        :not-found (format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                           native-id concept-id)))
    (let [concept (-> concept-attribs
                      (dissoc :provider-id :native-id)
                      (assoc :concept-id concept-id :deleted true))
          {:keys [revision-id]} (mdb/save-concept context concept)]
      {:concept-id concept-id, :revision-id revision-id})))

(defn reset
  "Resets the queue broker"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (queue/reset queue-broker))
  (cache/reset-caches context))

(def health-check-fns
  "A map of keywords to functions to be called for health checks"
  {:oracle #(conn/health (pah/context->db %))
   :echo rest/health
   :metadata-db mdb2/get-metadata-db-health
   :indexer indexer/get-indexer-health
   :cubby cubby/get-cubby-health
   :message-queue #(queue/health (get-in % [:system :queue-broker]))})

(defn health
  "Returns the health state of the app."
  [context]
  (let [dep-health (util/map-values #(% context) health-check-fns)
        ok? (every? :ok? (vals dep-health))]
    {:ok? ok?
     :dependencies dep-health}))
