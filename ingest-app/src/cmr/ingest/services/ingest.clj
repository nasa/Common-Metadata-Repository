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

(defn- add-extra-fields-for-collection
  "Adds the extra fields for a collection concept."
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

(defn validate-collection
  "Validate the collection. Returns an appropriate error message indicating any validation failures."
  [context concept]
  (v/validate-concept-request concept)
  (v/validate-concept-xml concept)

  (let [collection (umm/parse-concept concept)
        ;; UMM Validation
        _ (when (ingest-validation-enabled?)
            (v/validate-collection-umm collection))
        ;; Add extra fields for the collection
        coll-concept (add-extra-fields-for-collection context concept collection)]
    (v/validate-business-rules context coll-concept)
    coll-concept))

(defn- get-granule-parent-collection-concept
  "Find the parent collection for a granule given its provider id and collection ref. This will
  correctly handle situations where there might be multiple concept ids that used a short name and
  version id or entry title but were previously deleted."
  [context concept granule]
  (let [provider-id (:provider-id concept)
        collection-ref (:collection-ref granule)
        params (util/remove-nil-keys (merge {:provider-id provider-id}
                                            collection-ref))]
    (or (first (h/find-visible-collections context params))
        (cmsg/data-error :bad-request
                         msg/parent-collection-does-not-exist
                         (:granule-ur granule)
                         (:collection-ref granule)))))

(defn- add-extra-fields-for-granule
  "Adds the extra fields for a granule concept."
  [context concept granule collection-concept]
  (let [{:keys [collection-ref granule-ur]
         {:keys [delete-time]} :data-provider-timestamps} granule
        parent-collection-id (:concept-id collection-concept)]
    (assoc concept :extra-fields {:parent-collection-id parent-collection-id
                                  :delete-time (when delete-time (str delete-time))})))

(defn validate-granule
  "Validate a granule concept actually ingesting it. Return an appropriate error message indicating
  any validation failures.

  Accepts an optional function for looking up the parent collection concept. This can be used to
  provide the collection through an alternative means like the API."
  ([context concept]
   (validate-granule
     context concept get-granule-parent-collection-concept))
  ([context concept fetch-parent-collection-concept-fn]
   (v/validate-concept-request concept)
   (v/validate-concept-xml concept)

   (let [granule (umm/parse-concept concept)
         parent-collection-concept (fetch-parent-collection-concept-fn
                                     context concept granule)]
     ;; UMM Validation
     (when (ingest-validation-enabled?)
       (let [parent-collection (umm/parse-concept parent-collection-concept)]
         (v/validate-granule-umm parent-collection granule)))

     ;; Add extra fields for the granule
     (let [gran-concept (add-extra-fields-for-granule
                          context concept granule parent-collection-concept)]
       (v/validate-business-rules context gran-concept)
       gran-concept))))

(defmulti validate-concept
  "Validate that a concept is valid for ingest without actually ingesting the concept.
  Return an appropriate error message indicating any validation failures."
  (fn [context concept]
    (:concept-type concept)))

(defmethod validate-concept :collection
  [context concept]
  (validate-collection context concept))

(defmethod validate-concept :granule
  [context concept]
  (validate-granule context concept))

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
