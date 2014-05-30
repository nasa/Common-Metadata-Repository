(ns cmr.bootstrap.data.bulk-migration
  (:require [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [cmr.common.config :as config]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [sqlingvo.core :as sql :refer [sql select insert from where with order-by desc delete as]]
            [sqlingvo.vendor :as v]
            [cmr.metadata-db.data.oracle.sql-utils :as su]
            [clojure.core.async :as ca :refer [thread alts!! <!!]]
            [cmr.oracle.connection :as oc]))

;; To copy a provider
;; 1. Tell the metadata db to drop the provider
;; 2. Tell the metadata db to create the provider
;; 3. Insert collections by selecting from dataset table.
;; 4. Iterate over dataset ids for provider and insert granules into metadata db table
;; by selecting from catlog-rest table.

(def catalog-rest-tablespace "DEV_52_CATALOG_REST")

(def metadata-db-tablespace "METADATA_DB")

(def metadata-db-port (config/config-value-fn :metadata-db-port 3001))

(defn create-provider-url
  []
  (format "http://localhost:%s/providers" (metadata-db-port)))

(defn delete-provider-url
  [provider-id]
  (format "http://localhost:%s/providers/%s" (metadata-db-port) provider-id))

(defn- create-provider
  "Create the provider with the given provider id"
  [provider-id]
  (client/post (create-provider-url)
               {:body (format "{\"provider-id\": \"%s\"}" provider-id)
                :content-type :json}))

(defn- delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [provider-id]
  (client/delete (delete-provider-url provider-id)
                 {:throw-exceptions false}))

(defn- get-provider-collection-list-sql
  "Get the list of collections (datasets) for the given provider."
  [provider-id]
  (let [dataset-table (keyword (str catalog-rest-tablespace "." provider-id "-dataset-records"))]
    (su/build (select [:id :dataset-id] (from dataset-table)))))

(defn- get-provider-collection-list
  "Get the list of collections (datasets) for the given provider."
  [db provider-id]
  (j/with-db-transaction
    [conn db]
    (j/query conn (get-provider-collection-list-sql provider-id))))

(defn- copy-collection-data-sql
  "Generate SQL to copy the dataset/collection data from the catalog rest database to the
  metadata db for the given provider."
  [provider-id]
  (let [dataset-table (keyword (str catalog-rest-tablespace "." provider-id "-dataset-records"))
        collection-table (keyword (str metadata-db-tablespace "." provider-id "-collections"))
        sequence (keyword (str metadata-db-tablespace ".concept_id_seq.NEXTVAL"))]
    (su/build (insert collection-table [:concept-id
                                        :native-id
                                        :metadata
                                        :format
                                        :short-name
                                        :version-id
                                        :entry-title]
                      (select [:echo-collection-id
                               :dataset-id
                               :compressed-xml
                               :xml-mime-type
                               :short-name
                               :version-id
                               :long-name]
                              (from dataset-table))))))


(defn- copy-collection-data
  "Copy the dataset/collection data from the catalog rest database to the metadata db
  for the given provider."
  [db provider-id]
  (j/with-db-transaction
    [conn db]
    (j/execute! conn (copy-collection-data-sql provider-id))))

(defn- copy-granule-data-for-collection-sql
  "Generate the SQL to copy the granule data from the catalog reset datbase to the metadata db."
  [provider-id collection-id dataset-record-id]
  (let [granule-echo-table (keyword (str catalog-rest-tablespace "." provider-id "-granule-records"))
        granule-mdb-table (keyword (str metadata-db-tablespace "." provider-id "-metadata-db-tablespace"))
        sequence (keyword (str metadata-db-tablespace ".concept_id_seq.NEXTVAL"))]
    (su/build (insert granule-mdb-table [:concept-id
                                         :native-id
                                         :parent-collection-id
                                         :metadata
                                         :format]
                      (select [:echo-granule-id
                               :granule-ur
                               collection-id
                               :compressed-xml
                               :xml-mime-type]
                              (from granule-echo-table)
                              (where `(= :dataset-record-id ~dataset-record-id)))))))

(defn- copy-granule-data-for-collection
  "Copy the granule data from the catalog reset datbase to the metadata db."
  [db provider-id collection-id dataset-record-id]
  (j/with-db-transaction
    [conn db]
    (j/execute! conn (copy-granule-data-for-collection-sql provider-id collection-id dataset-record-id))))

(defn- copy-granule-data-for-provider
  "Copy the granule data for every collection for a given provider."
  [db provider-id]
  (for [{:keys [id collection_id]} (get-provider-collection-list db provider-id)]
    (do
      (println id "-" collection_id)
      (copy-granule-data-for-collection db provider-id collection_id id))))

(defn copy-provider
  "Copy all data for a given provider (including datasets and granules from the catalog-rest
  database into the metadata db database."
  [db provider-id]
  (delete-provider provider-id)
  (create-provider provider-id)
  (copy-collection-data db provider-id)
  (copy-granule-data-for-provider db provider-id))

;; Background task to handle requests
(defn handle-copy-requests
  "Handle any requests for copying data from echo catalog rest to metadata db."
  [system]
  (info "Starting background task for monitoring bulk migration channels.")
  (let [db (:db system)
        channels ((juxt :provider-channel) system)] ; add other channels as needed
    (thread (while true
              (let [[v ch] (alts!! channels)]
                (cond
                  ;; add other channels as needed
                  (= (:provider-channel system) ch)
                  ;(copy-provider (:db context))
                  (info "Processing provider" v)))))))


(comment
  (delete-provider "PROV1")
  (get-provider-collection-list (oc/create-db (oc/db-spec)) "FIX_PROV1")
  (copy-provider (oc/create-db (oc/db-spec)) "FIX_PROV1")
  (get-provider-collection-list nil "PROV1")
  (copy-collection-data-sql "PROV1")
  (copy-granule-data-for-collection-sql "PROV1" "C1-PROV1" 1234)
  (copy-granule-data-for-provider (oc/create-db (oc/db-spec)) "FIX_PROV1")
  )
