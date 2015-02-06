(ns cmr.ingest.services.ingest
  (:require [cmr.oracle.connection :as conn]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.transmit.echo.rest :as rest]
            [cmr.ingest.data.indexer :as indexer]
            [cmr.ingest.data.provider-acl-hash :as pah]
            [cmr.ingest.services.messages :as msg]
            [cmr.ingest.services.validation :as v]
            [cmr.ingest.services.helper :as h]
            [cmr.ingest.config :as config]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as serv-errors]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.util :as util]
            [cmr.common.config :as cfg]
            [cmr.umm.core :as umm]
            [clojure.string :as string]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.cache :as cache]
            [cmr.system-trace.core :refer [deftracefn]]))

(def ingest-validation-enabled?
  "A configuration feature switch that turns on CMR ingest validation."
  (cfg/config-value-fn :ingest-validation-enabled "true" #(= % "true")))

(defn add-extra-fields-for-collection
  [context concept collection]
  (let [{{:keys [short-name version-id]} :product
         {:keys [delete-time]} :data-provider-timestamps
         entry-title :entry-title
         entry-id :entry-id} collection]
    (assoc concept :extra-fields {:entry-title entry-title
                                  :entry-id entry-id
                                  :short-name short-name
                                  :version-id version-id
                                  :delete-time (when delete-time (str delete-time))})))

(defn- get-granule-parent-collection-concept
  "Find the parent collection for a granule given its provider id and collection ref. This will
  correctly handle situations where there might be multiple concept ids that used a short name and
  version id or entry title but were previously deleted."
  [context provider-id collection-ref]
  (let [params (util/remove-nil-keys (merge {:provider-id provider-id}
                                            collection-ref))
        matching-concepts (h/find-visible-collections context params)]
    (when (> (count matching-concepts) 1)
      (serv-errors/internal-error!
        (format (str "Found multiple possible parent collections for a granule in provider %s"
                     " referencing with %s. matching-concepts: %s")
                provider-id (pr-str collection-ref) (pr-str matching-concepts))))
    (first matching-concepts)))

(defn add-extra-fields-for-granule
  [context concept granule collection-concept]
  (let [{:keys [collection-ref granule-ur]
         {:keys [delete-time]} :data-provider-timestamps} granule
        parent-collection-id (:concept-id collection-concept)]
    (assoc concept :extra-fields {:parent-collection-id parent-collection-id
                                  :delete-time (when delete-time (str delete-time))})))

(defmulti prepare-concept-for-save
  "Prepares a concept for saving. This includes UMM validation and adding extra fields."
  (fn [context concept]
    (:concept-type concept)))

(defmethod prepare-concept-for-save :collection
  [context concept]
  (let [collection (umm/parse-concept concept)]
    ;; UMM Validation
    (when (ingest-validation-enabled?)
      (v/validate-collection-umm collection))
    ;; Add extra fields for the collection
    (add-extra-fields-for-collection context concept collection)))

(defmethod prepare-concept-for-save :granule
  [context concept]
  (let [granule (umm/parse-concept concept)
        parent-collection-concept (get-granule-parent-collection-concept
                                    context
                                    (:provider-id concept)
                                    (:collection-ref granule))]
    (when-not parent-collection-concept
      (cmsg/data-error :not-found
                       msg/parent-collection-does-not-exist
                       (:granule-ur granule)
                       (:collection-ref granule)))

    ;; UMM Validation
    (when (ingest-validation-enabled?)
      (let [parent-collection (umm/parse-concept parent-collection-concept)]
        (v/validate-granule-umm parent-collection granule)))

    ;; Add extra fields for the granule
    (add-extra-fields-for-granule context concept granule parent-collection-concept)))

(deftracefn validate-concept
  "Validate that a concept is valid for ingest without actually ingesting the concept.
  Return an appropriate error message indicating any validation failures."
  [context concept]
  (v/validate-concept-request concept)
  (v/validate-concept-xml concept)
  (let [concept (prepare-concept-for-save context concept)]
    (v/validate-business-rules context concept)
    concept))

(deftracefn save-concept
  "Store a concept in mdb and indexer and return concept-id and revision-id."
  [context concept]
  (let [concept (validate-concept context concept)]
    (let [{:keys [concept-id revision-id]} (mdb/save-concept context concept)]
      (indexer/index-concept context concept-id revision-id)
      {:concept-id concept-id, :revision-id revision-id})))

(deftracefn delete-concept
  "Delete a concept from mdb and indexer."
  [context concept-attribs]
  (let [{:keys [concept-type provider-id native-id]}  concept-attribs
        concept-id (mdb/get-concept-id context concept-type provider-id native-id)
        revision-id (mdb/delete-concept context concept-id)]
    (indexer/delete-concept-from-index context concept-id revision-id)
    {:concept-id concept-id, :revision-id revision-id}))

(deftracefn reset
  "Resets the queue broker"
  [context]
  (when (config/use-index-queue?)
    (let [queue-broker (get-in context [:system :queue-broker])]
      (queue/reset queue-broker)))
  (cache/reset-caches context))

(deftracefn health
  "Returns the health state of the app."
  [context]
  (let [db-health (conn/health (pah/context->db context))
        echo-rest-health (rest/health context)
        metadata-db-health (mdb/get-metadata-db-health context)
        indexer-health (indexer/get-indexer-health context)
        ok? (every? :ok? [db-health echo-rest-health metadata-db-health indexer-health])]
    {:ok? ok?
     :dependencies {:oracle db-health
                    :echo echo-rest-health
                    :metadata-db metadata-db-health
                    :indexer indexer-health}}))
