(ns cmr.ingest.services.ingest-service.util
  (:require
   [cheshire.core :as json]
   [cmr.common.cache :as cache]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.ingest.config :as config]
   [cmr.ingest.data.bulk-update :as bulk-update]
   [cmr.ingest.data.granule-bulk-update :as granule-bulk-update]
   [cmr.ingest.data.provider-acl-hash :as pah]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]
   [cmr.oracle.connection :as conn]
   [cmr.redis-utils.redis :as redis]
   [cmr.transmit.echo.rest :as rest]
   [cmr.transmit.indexer :as indexer]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]))

(defn fix-ingest-concept-format
  "Fixes formats"
  [concept-type fmt]
  (if (or
        (not (mt/umm-json? fmt))
        (mt/version-of fmt))
    fmt
    (str fmt ";version=" (config/ingest-accept-umm-version concept-type))))

(defn reset
  "Resets the queue broker"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (queue-protocol/reset queue-broker))
  (bulk-update/reset-db context)
  (granule-bulk-update/reset-db context)
  (cache/reset-caches context))

(def health-check-fns
  "A map of keywords to functions to be called for health checks"
  {:oracle #(conn/health (pah/context->db %))
   :echo rest/health
   :metadata-db mdb2/get-metadata-db-health
   :indexer indexer/get-indexer-health
   :message-queue #(queue-protocol/health (get-in % [:system :queue-broker]))})

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

(defn concept-json->concept
  "Returns the concept for the given concept JSON string.
  This is a temporary function and will be replaced by the UMM parse-metadata function once
  UMM-Var and UMM-Service are fully supported in UMM-Spec."
  [json-str]
  (util/map-keys->kebab-case
   (json/parse-string json-str true)))
