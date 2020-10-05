(ns cmr.metadata-db.data.oracle.sub-notifications
  "Provides implementations of the cmr.metadata-db.data.concepts/ConceptStore methods for OracleStore"
  (:require
   [clj-time.coerce :as cr]
   [clojure.java.jdbc :as j]
   [clojure.string :as string]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.date-time-parser :as p]
   [cmr.common.log :refer [debug error info trace warn]]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as t] ; don't use clj-time
   [cmr.common.util :as util]
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concept-tables :as tables]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]
   [cmr.metadata-db.data.util :as db-util :refer [EXPIRED_CONCEPTS_BATCH_SIZE INITIAL_CONCEPT_NUM]]
   [cmr.metadata-db.services.provider-service :as provider-service]
   [cmr.oracle.connection :as oracle]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import
   (cmr.oracle.connection OracleStore)))

; a note about prepared statments, j/query using ? and [] is the same as
; db-do-prepared. Do not use format function with %s

(defn dbresult->sub-notification
  "Converts a map result from the database to a provider map"
  [db data]
  (let [{:keys [id subscription_concept_id last_notified_at]} data]
    (j/with-db-transaction [conn db]
      {;:id id ; explicitly dropping id as DB specific need not be public
        :subscription-concept-id subscription_concept_id
        :last-notified-at (oracle/oracle-timestamp->str-time conn last_notified_at)
        })))

(defn subscription-exists?
  "Check to see if the subscription exists"
  [db subscription-id]
  (let [sql (str "SELECT concept_id FROM cmr_subscriptions WHERE concept_id = ?")
        result (j/query db [sql subscription-id])]
    (= (count result) 1)))

(defn sub-notification-exists?
 "Check to see if the subscription notification record exists"
  [db subscription-id]
  (let [sql (str "SELECT subscription_concept_id "
                       "FROM cmr_sub_notifications "
                       "WHERE subscription_concept_id = ?")
        result (j/query db [sql subscription-id])]
   (= (count result) 1)))

(defn get-sub-notification
  "Read in CRUD"
  [db subscription-id]
    (let [sql (str "SELECT id, subscription_concept_id, last_notified_at "
                   "FROM cmr_sub_notifications "
                   "WHERE subscription_concept_id = ?")
          results (first (j/query db [sql subscription-id]))]
          (dbresult->sub-notification db results)))

(defn save-sub-notification
  "Create in CRUD"
  [db subscription-id]
  (let [sql (str "INSERT INTO cmr_sub_notifications"
                 "(id, subscription_concept_id)"
                 "VALUES (cmr_sub_notifications_seq.nextval, ?)")]
  (j/db-do-prepared db sql [subscription-id])))

(defn update-sub-notification
  "Update in CRUD - return same results as get-sub-notification"
  [db subscription-id]
  (let [sql (str "UPDATE cmr_sub_notifications "
                 "SET last_notified_at = ? "
                 "WHERE subscription_concept_id = ?")
        now (t/now)]
    (j/db-do-prepared db sql [(cr/to-sql-time now) subscription-id])))

(defn delete-sub-notification
  "Delete in CRUD"
  [db subscription-id]
  (j/delete! db
   "cmr_sub_notifications"
   ["subscription_concept_id = ?" subscription-id]))

(def behaviour
 {:save-cmr-sub-notifications save-sub-notification
  :get-cmr-sub-notification get-sub-notification
  :update-cmr-sub-notifications update-sub-notification
  :delete-cmr-sub-notifications delete-sub-notification
  ;:reset-cmr-sub-notifications reset-sub-notification
  })

;; TODO: no idea what this is for - should it be fixed or removed
;(extend OracleStore
;        p/ProvidersStore
;        behaviour)

(comment
  (def db (get-in user/system [:apps :metadata-db :db]))

  (println (subscription-exists? db "SUB1234-test"))
  (println (sub-notification-exists? db "SUB1234-test"))

  ;crud tests
  (println (save-sub-notification db "SUB1234-test"))
  (println (sub-notification-exists? db "SUB1234-test"))
  (println (get-sub-notification db "SUB1234-test"))
  (println (update-sub-notification db "SUB1234-test"))
  (println (delete-sub-notification db "SUB1234-test"))
)
