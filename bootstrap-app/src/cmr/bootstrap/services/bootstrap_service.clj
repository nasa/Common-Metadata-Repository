(ns cmr.bootstrap.services.bootstrap-service
  "Provides methods to insert migration requets on the approriate channels."
  (:require [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as err]
            [cmr.bootstrap.data.bulk-index :as bulk]
            [cmr.bootstrap.data.bulk-migration :as bm]
            [cmr.bootstrap.data.db-synchronization :as dbs]))

(defn migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [context provider-id synchronous]
  (if synchronous
    (bm/copy-provider (:system context) provider-id)
    (let [channel (get-in context [:system :provider-db-channel])]
      (info "Adding provider" provider-id "to provider channel")
      (go (>! channel provider-id)))))

(defn migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [context provider-id collection-id synchronous]
  (if synchronous
    (bm/copy-single-collection (:system context) provider-id collection-id)
    (let [channel (get-in context [:system :collection-db-channel])]
      (info "Adding collection"  collection-id "for provider" provider-id "to collection channel")
      (go (>! channel {:collection-id collection-id :provider-id provider-id})))))

(defn- get-provider
  "Returns the metadata db provider that matches the given provider id. Throws exception if
  no matching provider is found."
  [context provider-id]
  (if-let [provider (bulk/get-provider-by-id context provider-id)]
    provider
    (err/throw-service-errors :bad-request
                              [(format "Provider: [%s] does not exist in the system" provider-id)])))

(defn validate-collection
  "Validates to be bulk_indexed collection exists in cmr else an exception is thrown."
  [context provider-id collection-id]
  (let [provider (get-provider context provider-id)]
    (when-not (bulk/get-collection context provider collection-id)
      (err/throw-service-errors :bad-request
                                [(format "Collection [%s] does not exist." collection-id)]))))

(defn index-provider
  "Bulk index all the collections and granules for a provider."
  [context provider-id synchronous start-index]
  (get-provider context provider-id)
  (if synchronous
    (bulk/index-provider (:system context) provider-id start-index)
    (let [channel (get-in context [:system :provider-index-channel])]
      (info "Adding provider" provider-id "to provider index channel")
      (go (>! channel {:provider-id provider-id
                       :start-index start-index})))))

(defn index-collection
  "Bulk index all the granules in a collection"
  [context provider-id collection-id synchronous]
  (validate-collection context provider-id collection-id)
  (if synchronous
    (bulk/index-granules-for-collection (:system context) provider-id collection-id)
    (let [channel (get-in context [:system :collection-index-channel])]
      (info "Adding collection" collection-id "to collection index channel")
      (go (>! channel [provider-id collection-id])))))

(defn db-synchronize
  "Synchronizes Catalog REST and Metadata DB looking for differences that were ingested between
  start date and end date"
  [context synchronous params]
  (if synchronous
    (dbs/synchronize-databases (:system context) params)
    (let [channel (get-in context [:system :db-synchronize-channel])]
      (info "Adding message to the database synchronize channel.")
      (go (>! channel params)))))