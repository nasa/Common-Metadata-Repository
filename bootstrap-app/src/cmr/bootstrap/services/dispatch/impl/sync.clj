(ns cmr.bootstrap.services.dispatch.impl.sync
  "Functions implementing the dispatch protocol to support synchronous calls."
  (:require
   [cmr.bootstrap.data.bulk-index :as bulk-index]
   [cmr.bootstrap.data.bulk-migration :as bulk-migration]
   [cmr.bootstrap.data.fingerprint :as fingerprint]
   [cmr.bootstrap.data.virtual-products :as virtual-products]))

(defn- migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [_this context provider-id]
  (bulk-migration/copy-provider (:system context) provider-id))

(defn- migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [_this context provider-id collection-id]
  (bulk-migration/copy-single-collection (:system context) provider-id collection-id))

(defn- index-provider
  "Bulk index all the collections and granules for a provider."
  [_this context provider-id start-index]
  (bulk-index/index-provider (:system context) provider-id start-index))

(defn- index-data-later-than-date-time
  "Bulk index all the concepts with a revision date later than the given date-time."
  [_this context provider-ids date-time]
  (bulk-index/index-data-later-than-date-time (:system context) provider-ids date-time))

(defn- index-collection
  "Bulk index all the granules in a collection"
  [_this context provider-id collection-id options]
  (bulk-index/index-granules-for-collection (:system context) provider-id collection-id options))

(defn- index-system-concepts
  "Bulk index all the tags, acls, and access-groups."
  [_this context start-index]
  (bulk-index/index-system-concepts (:system context) start-index))

(defn- index-concepts-by-id
  "Bulk index the concepts given by the concept-ids"
  [_this context provider-id concept-type concept-ids]
  (bulk-index/index-concepts-by-id (:system context) provider-id concept-type concept-ids))

(defn- index-variables
  "Bulk index the variables in CMR. If a provider-id is given, only index the
  variables for that provider."
  ([_this context]
   (bulk-index/index-all-concepts (:system context) :variable))
  ([_this context provider-id]
   (bulk-index/index-provider-concepts (:system context) :variable provider-id)))

(defn- index-services
  "Bulk index the services in CMR. If a provider-id is given, only index the
  services for that provider."
  ([_this context]
   (bulk-index/index-all-concepts (:system context) :service))
  ([_this context provider-id]
   (bulk-index/index-provider-concepts (:system context) :service provider-id)))

(defn- index-tools
  "Bulk index the tools in CMR. If a provider-id is given, only index the
  tools for that provider."
  ([_this context]
   (bulk-index/index-all-concepts (:system context) :tool))
  ([_this context provider-id]
   (bulk-index/index-provider-concepts (:system context) :tool provider-id)))

(defn- index-subscriptions
  "Bulk index the subscriptions in CMR. If a provider-id is given, only index the
  subscriptions for that provider."
  ([_this context]
   (bulk-index/index-all-concepts (:system context) :subscription))
  ([_this context provider-id]
   (bulk-index/index-provider-concepts (:system context) :subscription provider-id)))

(defn- index-generics
  "Bulk index the generic documents of type concept-type in CMR. If a provider-id is given, only index the
  concept-type for that provider."
  ([_this context concept-type]
   (bulk-index/index-all-concepts (:system context) (keyword concept-type)))
  ([_this context concept-type provider-id]
   (bulk-index/index-provider-concepts (:system context) (keyword concept-type) provider-id)))

(defn- migrate-index
  "Copy the contents of one index to another. Used during resharding."
  [_this context source-index target-index elastic-name]
  (bulk-index/migrate-index (:system context) source-index target-index elastic-name))

(defn- delete-concepts-from-index-by-id
  "Bulk delete the concepts given by the concept-ids from the indexes"
  [_this context provider-id concept-type concept-ids]
  (bulk-index/delete-concepts-by-id (:system context) provider-id concept-type concept-ids))

(defn- bootstrap-virtual-products
  "Initializes virtual products for the given provider and entry title."
  [_this context provider-id entry-title]
  (virtual-products/bootstrap-virtual-products (:system context) provider-id entry-title))

(defn- fingerprint-by-id
  "Update the fingerprint of the variable with the given concept id if necessary."
  [_this context concept-id]
  (fingerprint/fingerprint-by-id (:system context) concept-id))

(defn- fingerprint-variables
  "Update the fingerprints of variables specified by params if necessary."
  ([_this context]
   (fingerprint/fingerprint-variables (:system context)))
  ([_this context provider-id]
   (fingerprint/fingerprint-variables (:system context) provider-id)))

(defrecord SynchronousDispatcher [])

(def dispatch-behavior
  "Map of protocol definitions to the implementations of that protocol for the synchronous
  dispatcher."
  {:migrate-provider migrate-provider
   :migrate-collection migrate-collection
   :index-provider index-provider
   :index-variables index-variables
   :index-services index-services
   :index-tools index-tools
   :index-subscriptions index-subscriptions
   :index-generics index-generics
   :index-data-later-than-date-time index-data-later-than-date-time
   :index-collection index-collection
   :index-system-concepts index-system-concepts
   :index-concepts-by-id index-concepts-by-id
   :migrate-index migrate-index
   :fingerprint-by-id fingerprint-by-id
   :fingerprint-variables fingerprint-variables
   :delete-concepts-from-index-by-id delete-concepts-from-index-by-id
   :bootstrap-virtual-products bootstrap-virtual-products})
