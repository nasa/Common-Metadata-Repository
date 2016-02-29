(ns cmr.metadata-db.data.oracle.providers
  "Functions for saving, retrieving, deleting providers."
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.providers :as p]
            [cmr.metadata-db.data.oracle.sql-helper :as sh]
            [cmr.metadata-db.data.oracle.concept-tables :as ct]
            [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore))

(defn dbresult->provider
  "Converts a map result from the database to a provider map"
  [{:keys [provider_id short_name cmr_only small]}]
  {:provider-id provider_id
   :short-name short_name
   :cmr-only (== 1 cmr_only)
   :small (== 1 small) })

(defn- delete-small-provider-concepts
  "Delete all concepts of the given small provider"
  [db provider]
  {:pre [(:small provider)]}
  (let [provider-id (:provider-id provider)]
    (sh/force-delete-concept-by-params db provider {:concept-type :granule
                                                    :provider-id provider-id})
    (sh/force-delete-concept-by-params db provider {:concept-type :collection
                                                    :provider-id provider-id})
    (sh/force-delete-concept-by-params db provider {:concept-type :service
                                                    :provider-id provider-id})
    ;; Cascade deletes to access groups. This does not remove the record from search.
    ;; access-control-app listens for :provider-delete messages and handles deleting the relevant
    ;; records from Elastic.
    (sh/force-delete-concept-by-params db provider {:concept-type :access-group
                                                    :provider-id provider-id})))

(extend-protocol p/ProvidersStore
  OracleStore

  (save-provider
    [db provider]
    (let [{:keys [provider-id short-name cmr-only small]} provider]
      (j/insert! db
                 :providers
                 ["provider_id" "short_name" "cmr_only" "small"]
                 [provider-id short-name (if cmr-only 1 0) (if small 1 0)])
      (when (not small)
        (ct/create-provider-concept-tables db provider))))

  (get-providers
    [db]
    (map dbresult->provider
         (j/query db ["SELECT * FROM providers"])))

  (get-provider
    [db provider-id]
    (first (map dbresult->provider
                (j/query db
                         ["SELECT provider_id, short_name, cmr_only, small FROM providers where provider_id = ?"
                          provider-id]))))

  (update-provider
    [db {:keys [provider-id short-name cmr-only]}]
    (j/update! db
               :providers
               {:short_name short-name
                :cmr_only (if cmr-only 1 0)}
               ["provider_id = ?" provider-id]))

  (delete-provider
    [db provider]
    (let [{:keys [provider-id small]} provider]
      (if small
        (delete-small-provider-concepts db provider)
        (ct/delete-provider-concept-tables db provider))
      (j/delete! db :providers ["provider_id = ?" provider-id])))

  (reset-providers
    [db]
    (doseq [provider (p/get-providers db)]
      (p/delete-provider db provider))))


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
