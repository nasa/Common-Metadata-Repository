(ns cmr.bootstrap.data.migration-utils
  "Contains utility functions for database migrations"
  (:require [cmr.metadata-db.data.oracle.concept-tables :as tables]))

(defn catalog-rest-user
  [system]
  (get-in system [:catalog-rest-user]))

(defn metadata-db-user
  [system]
  (get-in system [:db :spec :user]))

(defn provider-id->provider
  "Helper function to convert a provider id into a provider map.
  All catalog-rest providers map to CMR providers that has both cmr-only and small fields false."
  [provider-id]
  {:provider-id provider-id :short-name provider-id :cmr-only false :small false})

(defn metadata-db-concept-table
  [provider-id concept-type]
  (tables/get-table-name (provider-id->provider provider-id) concept-type))

(defn full-metadata-db-concept-table
  "Get the collection/granule table name for a given provider."
  [system provider-id concept-type]
  (str (metadata-db-user system) "."
       (metadata-db-concept-table provider-id concept-type)))

(def concept-type->catalog-rest-id-field
  {:granule "echo_granule_id"
   :collection "echo_collection_id"})

(def concept-type->catalog-rest-table-prefix
  {:granule "granule"
   :collection "dataset"})

(defn catalog-rest-table
  [system provider-id concept-type]
  (format "%s.%s_%s_records"
          (catalog-rest-user system)
          provider-id
          (concept-type->catalog-rest-table-prefix concept-type)))

(def CATALOG_REST_SKIPPED_ITEMS_CLAUSE
  "A sql clause that will skip items with xml mime types that Metadata DB does not support."
  "xml_mime_type not in ('ISO', 'GRACE_ISO', 'ISO-GRACE')")
