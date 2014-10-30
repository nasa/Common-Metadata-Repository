(ns cmr.bootstrap.data.migration-utils
  "Contains utility functions for database migrations"
  (:require [cmr.metadata-db.data.oracle.concept-tables :as tables]))


(defn catalog-rest-user
  [system]
  (get-in system [:catalog-rest-user]))

(defn metadata-db-user
  [system]
  (get-in system [:db :spec :user]))

(defn metadata-db-concept-table
  "Get the collection/granule table name for a given provider."
  [system provider-id concept-type]
  (str (metadata-db-user system) "." (tables/get-table-name provider-id concept-type)))

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