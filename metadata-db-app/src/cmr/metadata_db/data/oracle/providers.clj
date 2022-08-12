(ns cmr.metadata-db.data.oracle.providers
  "Functions for saving, retrieving, deleting providers."
  (:require
   [clojure.java.jdbc :as j]
   [clojure.pprint :refer [pprint pp]]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.oracle.concept-tables :as ct]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]
   [cmr.metadata-db.data.providers :as p]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc
                                        delete as]])
  (:import
   (cmr.oracle.connection OracleStore)))

(defn dbresult->provider
  "Converts a map result from the database to a provider map"
  [{:keys [provider_id short_name cmr_only small consortiums]}]
  (cutil/remove-nil-keys {:provider-id provider_id
                          :short-name short_name
                          :cmr-only (== 1 cmr_only)
                          :small (== 1 small)
                          :consortiums consortiums}))

(defn- delete-small-provider-concepts
  "Delete all concepts of the given small provider"
  [db provider]
  {:pre [(:small provider)]}
  (let [provider-id (:provider-id provider)]
    (sh/force-delete-concept-by-params db provider {:concept-type :granule
                                                    :provider-id provider-id})
    (sh/force-delete-concept-by-params db provider {:concept-type :collection
                                                    :provider-id provider-id})))

(defn- purge-provider-data
  "Purges all provider data from the system. When a provider is deleted its concepts are permanently
   deleted from Oracle and not tombstoned. If the provider is a 'small' provider its data is deleted
   from the respective common tables. If it is a non-'small' provider then its various concept
   tables are simply dropped from the database schema. Access Groups are always in a common table
   and have the same logic for deletion regardless of whether the provider is 'small' or non-'small'."
  [db provider]
  (let [{:keys [provider-id small]} provider]
    ;; Access Group deletion is the same between all provider types. This does not remove the record
    ;; from search. access-control-app listens for :provider-delete messages and handles deleting the
    ;; relevant records from Elastic.
    (j/delete! db (ct/get-table-name provider :access-group) ["provider_id = ?" provider-id])
    ;; ACLs are removed in a way similar to access groups
    (j/delete! db (ct/get-table-name provider :acl) ["target_provider_id = ?" provider-id])
    (if small
      (delete-small-provider-concepts db provider)
      (ct/delete-provider-concept-tables db provider))

    ;; Delete the variable associations related to the provider via variable
    (j/db-do-commands db (str "DELETE FROM CMR_ASSOCIATIONS where association_type = 'VARIABLE-COLLECTION' and source_concept_identifier like 'V%-" provider-id "'"))
    ;; Delete the variable associations related to the provider via collection
    (j/db-do-commands db (str "DELETE FROM CMR_ASSOCIATIONS where association_type = 'VARIABLE-COLLECTION' and associated_concept_id like 'C%-" provider-id "'"))
    ;; Delete variables of the provider
    (j/delete! db (ct/get-table-name provider :variable) ["provider_id = ?" provider-id])

    ;; Delete the service associations related to the provider via service
    (j/db-do-commands db (str "DELETE FROM CMR_ASSOCIATIONS where association_type = 'SERVICE-COLLECTION' and source_concept_identifier like 'S%-" provider-id "'"))
    ;; Delete the service associations related to the provider via collection
    (j/db-do-commands db (str "DELETE FROM CMR_ASSOCIATIONS where association_type = 'SERVICE-COLLECTION' and associated_concept_id like 'C%-" provider-id "'"))
    ;; Delete services of the provider
    (j/delete! db (ct/get-table-name provider :service) ["provider_id = ?" provider-id])

    ;; Delete the tool associations related to the provider via tool
    (j/db-do-commands db (str "DELETE FROM CMR_ASSOCIATIONS where association_type = 'TOOL-COLLECTION' and source_concept_identifier like 'TL%-" provider-id "'"))
    ;; Delete the tool associations related to the provider via collection
    (j/db-do-commands db (str "DELETE FROM CMR_ASSOCIATIONS where association_type = 'TOOL-COLLECTION' and associated_concept_id like 'C%-" provider-id "'"))
    ;; Delete tools of the provider
    (j/delete! db (ct/get-table-name provider :tool) ["provider_id = ?" provider-id])

    ;; Delete subscriptions of the provider
    (j/delete! db (ct/get-table-name provider :subscription) ["provider_id = ?" provider-id])
    (j/delete! db :providers ["provider_id = ?" provider-id])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  [db provider]
  (let [{:keys [provider-id short-name cmr-only small consortiums]} provider]
    (j/insert! db
               :providers
               ["provider_id" "short_name" "cmr_only" "small" "consortiums"]
               [provider-id short-name (if cmr-only 1 0) (if small 1 0) consortiums])
    (when (not small)
      (ct/create-provider-concept-tables db provider))))

(defn get-providers
  [db]
  (map dbresult->provider
       (j/query db ["SELECT * FROM providers"])))

(defn get-provider
  [db provider-id]
  (first (map dbresult->provider
              (j/query db
                       [(str "SELECT provider_id, short_name, cmr_only, small, consortiums"
                             " FROM providers"
                             " WHERE provider_id = ?")
                        provider-id]))))

(defn update-provider
  [db {:keys [provider-id short-name cmr-only consortiums]}]
  (j/update! db
             :providers
             {:short_name short-name
              :cmr_only (if cmr-only 1 0)
              :consortiums consortiums}
             ["provider_id = ?" provider-id]))

(defn delete-provider
  [db provider]
  (purge-provider-data db provider))

(defn reset-providers
  [db]
  (doseq [provider (get-providers db)]
    (delete-provider db provider)))

(def behaviour
  {:save-provider save-provider
   :get-providers get-providers
   :get-provider get-provider
   :update-provider update-provider
   :delete-provider delete-provider
   :reset-providers reset-providers})

(extend OracleStore
        p/ProvidersStore
        behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (def db (get-in user/system [:apps :metadata-db :db]))

  (get-providers db)
  (reset-providers db)
  (delete-provider db "PROV1")
  (delete-provider db "FOO")

  (->> (j/query db ["SELECT count(1) FROM providers where provider_id = ?" provider-id])
       first vals first (== 0))

  (j/delete! db :providers ["provider_id = ?" "FOO"]))
