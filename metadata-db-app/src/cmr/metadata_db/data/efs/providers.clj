(ns cmr.metadata-db.data.efs.providers
  "Functions for saving, retrieving, deleting providers."
  (:require
   [clojure.pprint :refer [pprint pp]]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.providers :as p])
  (:import
   (cmr.efs.connection EfsStore)))

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
  ())

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  [db provider]
  ())

(defn get-providers
  [db]
  ())

(defn get-provider
  [db provider-id]
  ())

(defn update-provider
  [db {:keys [provider-id short-name cmr-only consortiums]}]
  ())

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

(extend EfsStore
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
