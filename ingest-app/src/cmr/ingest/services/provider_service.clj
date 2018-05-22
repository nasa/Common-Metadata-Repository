(ns cmr.ingest.services.provider-service
  "Functions for CRUD operations on providers. All functions return
  the underlying Metadata DB API clj-http response which can be used
  as a Ring response."
  (:require
   [cmr.common.services.errors :as errors]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.transmit.metadata-db :as mdb]))

(defn verify-empty-provider
  "Throws error if provider still has collections."
  [context provider headers]
  (when-not (and (= "true"
                    (get headers "force-full-provider-delete"))
                 ;; The following does not use cmr.common.config due to the fact that
                 ;; this is a non-CMR env/config value; this is evident by the lack of the CMR-
                 ;; prefix.
                 (not= "prod"
                       (System/getenv "ENVIRONMENT")))
    (let [collections (mdb/find-collections context {:provider-id provider
                                                     :latest true})
          non-deleted-colls (remove #(= true (:deleted %)) collections)]
      (when-not (empty? non-deleted-colls)
        (errors/throw-service-error
         :unauthorized
         "You cannot perform this action on a provider that has collections.")))))

(defn- successful?
  "Returns true if the mdb response was successful."
  [response]
  (<= 200 (:status response) 299))

(defn create-provider
  "Create a provider."
  [context provider]
  (let [response (mdb/create-provider-raw context provider)]
    (when (successful? response)
      (ingest-events/publish-provider-event
        context (ingest-events/provider-create-event (:provider-id provider))))
    response))

(defn update-provider
  "Update an existing provider."
  [context provider]
  (let [response (mdb/update-provider-raw context provider)]
    (when (successful? response)
      (ingest-events/publish-provider-event
        context (ingest-events/provider-update-event (:provider-id provider))))
    response))

(defn delete-provider
  "Delete a provider and all its concepts."
  [context provider-id]
  (let [response (mdb/delete-provider-raw context provider-id)]
    (when (successful? response)
      (ingest-events/publish-provider-event
        context (ingest-events/provider-delete-event provider-id)))
    response))

(defn get-providers-raw
  "Get a list of provider ids in raw http response."
  [context]
  (mdb/get-providers-raw context))

(defn get-providers
  "Get a list of provider ids"
  [context]
  (mdb/get-providers context))
