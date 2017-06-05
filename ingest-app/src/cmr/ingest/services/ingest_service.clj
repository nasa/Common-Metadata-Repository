(ns cmr.ingest.services.ingest-service
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [cmr.common.api.context :as context-util]
    [cmr.common.cache :as cache]
    [cmr.common.config :as cfg :refer [defconfig]]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.services.messages :as cmsg]
    [cmr.common.util :as util :refer [defn-timed]]
    [cmr.common.validations.core :as cv]
    [cmr.ingest.config :as config]
    [cmr.ingest.data.bulk-update :as bulk-update]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; General Support/Utility Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fix-ingest-concept-format
  "Fixes formats"
  [fmt]
  (if (or
        (not (mt/umm-json? fmt))
        (mt/version-of fmt))
    fmt
    (str fmt ";version=" (config/ingest-accept-umm-version))))

(defn reset
  "Resets the queue broker"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (queue/reset queue-broker))
  (bulk-update/reset-db context)
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

(defmulti concept-json->concept
  "Converts the concept in JSON format to a standard Clojure data structure. It
  is expected that this function will be used to parse a request's body."
  type)

(defmethod concept-json->concept java.io.ByteArrayInputStream
  [data]
  (concept-json->concept (slurp data)))

(defmethod concept-json->concept java.lang.String
  [data]
  (util/map-keys->kebab-case
   (json/parse-string data true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Collection Service Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
        ;; Throw errors for validation on sanitized collection
        _ (v/umm-spec-validate-collection sanitized-collection validation-options context false)
        ;; Return warnings for schema validation errors going from xml -> UMM
        warnings (v/validate-collection-umm-spec-schema collection validation-options)
        ;; Return warnings for validation errors on collection without sanitization
        collection-warnings (concat
                             (v/umm-spec-validate-collection collection validation-options context true)
                             (v/umm-spec-validate-collection-warnings
                              collection validation-options context))
        collection-warnings (map #(str (:path %) " " (string/join " " (:errors %)))
                                 collection-warnings)
        warnings (concat warnings collection-warnings)]
    ;; The sanitized UMM Spec collection is returned so that ingest does not fail
    {:collection sanitized-collection
     :warnings warnings}))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Granule Service Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Variable Ingest Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;; Note that next section is primarily inspired by the code seen in the
;;; following locations:
;;;
;;; * cmr.search.api.tags-api
;;; * cmr.search.services.tagging-service
;;; * cmr.search.services.tagging.tag-validation
;;;
;;; The reason for this is that the code there is not only more recent and
;;; generally cleaner, it is so because it does not have all the history of
;;; constraints and special cases that the collection and granule code
;;; (above) does.

(def ^:private update-variable-validations
  "Service level validations when updating a variable."
  [(cv/field-cannot-be-changed :variable-name)
   ;; Originator id cannot change but we allow it if they don't specify a
   ;; value.
   (cv/field-cannot-be-changed :originator-id true)])

(defn validate-update-variable
  "Validates a variable update."
  [existing-variable updated-variable]
  (cv/validate!
   update-variable-validations
   (assoc updated-variable :existing existing-variable)))

(defn overwrite-variable-tombstone
  "This function is called when the variable exists but was previously
  deleted."
  [context concept variable user-id]
  (mdb2/save-concept context
                    (-> concept
                        (assoc :metadata (pr-str variable)
                               :deleted false
                               :user-id user-id)
                        (dissoc :revision-date)
                        (update-in [:revision-id] inc))))

(defn- variable->new-concept
  "Converts a variable into a new concept that can be persisted in metadata
  db."
  [variable]
  {:concept-type :variable
   :native-id (:native-id variable)
   :metadata (pr-str variable)
   :user-id (:originator-id variable)
   ;; The first version of a variable should always be revision id 1. We
   ;; always specify a revision id when saving variables to help avoid
   ;; conflicts
   :revision-id 1
   :format mt/edn
   :extra-fields {:variable-name (:name variable)
                  :measurement (:long-name variable)}})

(defn- fetch-variable-concept
  "Fetches the latest version of a variable concept variable variable-key."
  [context variable-key]
  (if-let [concept (mdb/find-latest-concept context
                                            {:native-id variable-key
                                             :latest true}
                                             :variable)]
    (if (:deleted concept)
      (errors/throw-service-error
       :not-found
       (msg/variable-deleted variable-key))
      concept)
    (errors/throw-service-error
     :not-found
     (msg/variable-does-not-exist variable-key))))

(defn-timed create-variable
  "Creates the variable, saving it as a revision in metadata db. Returns the
  concept id and revision id of the saved variable."
  [context variable-json-str]
  (let [user-id (context-util/context->user-id
                 context
                 msg/token-required-for-variable-modification)
        variable (as-> variable-json-str data
                       (concept-json->concept data)
                       (assoc data :originator-id user-id)
                       (assoc data :native-id (string/lower-case (:name data))))]
    ;; Check if the variable already exists
    (if-let [concept-id (mdb2/get-concept-id context
                                             :variable
                                             "CMR"
                                             (:native-id variable)
                                             false)]
      ;; The variable exists. Check if its latest revision is a tombstone
      (let [concept (mdb2/get-latest-concept context concept-id false)]
        (if (:deleted concept)
          (overwrite-variable-tombstone context concept variable user-id)
          (errors/throw-service-error
           :conflict
           (msg/variable-already-exists variable concept-id))))
      ;; The variable doesn't exist
      (mdb2/save-concept context
                         (variable->new-concept variable)))))

(defn update-variable
  "Updates an existing variable with the given concept id."
  [context variable-key variable-json-str]
  (let [updated-variable (concept-json->concept variable-json-str)
        existing-concept (fetch-variable-concept context variable-key)
        existing-variable (edn/read-string (:metadata existing-concept))]
    (validate-update-variable existing-variable updated-variable)
    (mdb/save-concept
      context
      (-> existing-concept
          ;; The updated variable won't change the originator of the existing
          ;; variable
          (assoc :metadata (-> updated-variable
                               (assoc :originator-id
                                      (:originator-id existing-variable))
                               (pr-str))
                 :user-id (context-util/context->user-id
                           context
                           msg/token-required-for-variable-modification))
          (dissoc :revision-date :transaction-id)
          (update-in [:revision-id] inc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service Ingest Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private update-service-validations
  "Service level validations when updating a service."
  [(cv/field-cannot-be-changed :service-name)
   ;; Originator id cannot change but we allow it if they don't specify a
   ;; value.
   (cv/field-cannot-be-changed :originator-id true)])

(defn validate-update-service
  "Validates a service update."
  [existing-service updated-service]
  (cv/validate!
   update-service-validations
   (assoc updated-service :existing existing-service)))

(defn overwrite-service-tombstone
  "This function is called when the service exists but was previously
  deleted."
  [context concept service user-id]
  (mdb2/save-concept context
                    (-> concept
                        (assoc :metadata (pr-str service)
                               :deleted false
                               :user-id user-id)
                        (dissoc :revision-date)
                        (update-in [:revision-id] inc))))

(defn- service->new-concept
  "Converts a service into a new concept that can be persisted in metadata
  db."
  [service]
  {:concept-type :service
   :native-id (:name service)
   :metadata (pr-str service)
   :user-id (:originator-id service)
   ;; The first version of a service should always be revision id 1. We
   ;; always specify a revision id when saving variables to help avoid
   ;; conflicts
   :revision-id 1
   :format mt/edn
   :provider-id "CMR"              ; XXX pending changes from @ygliuvt
   :extra-fields {
     :entry-id (:name service)     ; XXX pending changes from @ygliuvt
     :entry-title (:name service)  ; XXX pending changes from @ygliuvt
     :service-name (:name service)}})

(defn- fetch-service-concept
  "Fetches the latest version of a service concept service service-key."
  [context service-key]
  (if-let [concept (mdb/find-latest-concept context
                                            {:native-id service-key
                                             :latest true}
                                             :service)]
    (if (:deleted concept)
      (errors/throw-service-error
       :not-found
       (msg/service-deleted service-key))
      concept)
    (errors/throw-service-error
     :not-found
     (msg/service-does-not-exist service-key))))

(defn-timed create-service
  "Creates the service, saving it as a revision in metadata db. Returns the
  concept id and revision id of the saved service."
  [context service-json-str]
  (let [user-id (context-util/context->user-id
                 context
                 msg/token-required-for-service-modification)
        service (as-> service-json-str data
                       (concept-json->concept data)
                       (assoc data :originator-id user-id)
                       (assoc data :native-id (:name data)))]
    ;; Check if the service already exists
    (if-let [concept-id (mdb2/get-concept-id context
                                             :service
                                             "CMR"
                                             (:native-id service)
                                             false)]
      ;; The service exists. Check if its latest revision is a tombstone
      (let [concept (mdb2/get-latest-concept context concept-id false)]
        (if (:deleted concept)
          (overwrite-service-tombstone context concept service user-id)
          (errors/throw-service-error
           :conflict
           (msg/service-already-exists service concept-id))))
      ;; The service doesn't exist
      (mdb2/save-concept context
       (service->new-concept service)))))

(defn update-service
  "Updates an existing service with the given concept id."
  [context service-key service-json-str]
  (let [updated-service (concept-json->concept service-json-str)
        existing-concept (fetch-service-concept context service-key)
        existing-service (edn/read-string (:metadata existing-concept))]
    (validate-update-service existing-service updated-service)
    (mdb/save-concept
      context
      (-> existing-concept
          ;; The updated service won't change the originator of the existing
          ;; service
          (assoc :metadata (-> updated-service
                               (assoc :originator-id
                                      (:originator-id existing-service))
                               (pr-str))
                 :user-id (context-util/context->user-id
                           context
                           msg/token-required-for-service-modification))
          (dissoc :revision-date :transaction-id)
          (update-in [:revision-id] inc)))))
