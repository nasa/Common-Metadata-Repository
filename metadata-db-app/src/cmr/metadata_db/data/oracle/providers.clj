(ns cmr.metadata-db.data.oracle.providers
  "Functions for saving, retrieving, deleting providers."
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.providers :as p]
            [cmr.metadata-db.data.oracle.concept-tables :as ct]
            [cmr.metadata-db.data.oracle.sql-helper :as sh]
            [cmr.metadata-db.data.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore))

(defn dbresult->provider
  "Converts a map result from the database to a provider map"
  [{:keys [provider_id cmr_only small]}]
  {:provider-id provider_id
   :cmr-only (== 1 cmr_only)
   :small (== 1 small) })

(defn- provider->db-provider-id
  "Returns the database provider-id for the given provider"
  [provider]
  (let [{:keys [provider-id small]} provider]
    (if small
      p/small-provider-id
      provider-id)))

(defn- delete-small-provider-concepts
  "Delete all concepts of the given small provider"
  [db provider]
  {:pre [(:small provider)]}
  (let [provider-id (:provider-id provider)]
    (sh/force-delete-concept-by-params db provider {:concept-type :granule
                                                    :provider-id provider-id})
    (sh/force-delete-concept-by-params db provider {:concept-type :collection
                                                    :provider-id provider-id})))

(extend-protocol p/ProvidersStore
  OracleStore

  (save-provider
    [db provider]
    (try
      (let [{:keys [provider-id cmr-only small]} provider]
        (j/insert! db
                   :providers
                   ["provider_id" "cmr_only" "small"]
                   [provider-id (if cmr-only 1 0) (if small 1 0)])
        (when (not small)
          (ct/create-provider-concept-tables db provider)))
      (catch java.sql.BatchUpdateException e
        (let [error-message (.getMessage e)]
          (if (re-find #"UNIQUE_PROVIDER_ID" error-message)
            {:error :provider-id-conflict
             :error-message error-message}
            (throw e))))))

  (get-providers
    [db]
    (map dbresult->provider
         (j/query db ["SELECT provider_id, cmr_only, small FROM providers"])))

  (get-provider
    [db provider-id]
    (first (map dbresult->provider
                (j/query db
                         ["SELECT provider_id, cmr_only, small FROM providers where provider_id = ?"
                          provider-id]))))

  (update-provider
    [db {:keys [provider-id cmr-only small]}]
    (if-let [existing-provider (p/get-provider db provider-id)]
      (if (= small (:small existing-provider))
        (j/update! db
                   :providers
                   {:cmr_only (if cmr-only 1 0)
                    :small (if small 1 0)}
                   ["provider_id = ?" provider-id])
        (p/small-field-cannot-be-modified provider-id))
      (p/provider-not-found-error provider-id)))

  (delete-provider
    [db provider-id]
    (if-let [existing-provider (p/get-provider db provider-id)]
      (do
        (if (:small existing-provider)
          (delete-small-provider-concepts db existing-provider)
          (ct/delete-provider-concept-tables db existing-provider))
        (j/delete! db :providers ["provider_id = ?" provider-id]))
      (p/provider-not-found-error provider-id)))

  (reset-providers
    [db]
    (doseq [{:keys [provider-id]} (p/get-providers db)]
        (p/delete-provider db provider-id))))


(comment

  (def db (get-in user/system [:apps :metadata-db :db]))

  (p/get-providers db)
  (p/reset-providers db)
  (p/delete-provider db "PROV1")
  (p/delete-provider db "FOO")

  (->> (j/query db ["SELECT count(1) FROM providers where provider_id = ?" provider-id])
       first vals first (== 0))



  (j/delete! db :providers ["provider_id = ?" "FOO"])

  )
