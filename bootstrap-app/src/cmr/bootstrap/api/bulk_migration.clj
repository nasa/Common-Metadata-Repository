(ns cmr.bootstrap.api.bulk-migration
  "Defines the bulk migration functions for the bootstrap API."
  (:require
   [cmr.bootstrap.api.messages :as msg]
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.services.bootstrap-service :as service]))

(defn migrate-collection
  "Copy collections data from catalog-rest to metadata db (including granules)"
  [context provider-id-collection-map params]
  (let [dispatcher (api-util/get-dispatcher context params :migrate-collection)
        provider-id (get provider-id-collection-map "provider_id")
        collection-id (get provider-id-collection-map "collection_id")]
    (service/migrate-collection context dispatcher provider-id collection-id)
    {:status 202
     :body {:message (msg/migration-collection collection-id provider-id)}}))

(defn migrate-provider
  "Copy a single provider's data from catalog-rest to metadata db (including
  collections and granules)."
  [context provider-id-map params]
  (let [dispatcher (api-util/get-dispatcher context params :migrate-provider)
        provider-id (get provider-id-map "provider_id")]
    (service/migrate-provider context dispatcher provider-id)
    {:status 202
     :body {:message (msg/migration-provider provider-id)}}))
