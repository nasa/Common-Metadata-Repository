(ns cmr.metadata-db.data.oracle.sub-notifications
  "Provides implementations of the cmr.metadata-db.data.concepts/ConceptStore methods for OracleStore"
  (:require
   [clj-time.coerce :as cr]
   [clojure.java.jdbc :as j]
   [cmr.common.time-keeper :as t] ;; don't use clj-time
   [cmr.oracle.connection :as oracle]))

; A note about prepared statments, with j/query using ? and [] is the same as
; j/db-do-prepared. Do not use the string format function with %s as this could
; allow sql injection

(defn dbresult->sub-notification
  "Converts a map result from the database to a provider map"
  [db data]
  (let [{:keys [subscription_concept_id last_notified_at]} data]
    (j/with-db-transaction [conn db]
      {:subscription-concept-id subscription_concept_id
       :last-notified-at (oracle/oracle-timestamp->str-time conn last_notified_at)})))

(defn subscription-exists?
  "Check to see if the subscription exists"
  [db subscription-id]
  (let [sql (str "SELECT concept_id FROM cmr_subscriptions WHERE concept_id = ? FETCH FIRST 1 ROWS ONLY")
        result (j/query db [sql subscription-id])]
    (pos? (count result))))

(defn sub-notification-exists?
 "Check to see if the subscription notification record exists"
  [db subscription-id]
  (let [sql (str "SELECT subscription_concept_id "
                       "FROM cmr_sub_notifications "
                       "WHERE subscription_concept_id = ?"
                       "FETCH FIRST 1 ROWS ONLY")
        result (j/query db [sql subscription-id])]
   (= (count result) 1)))

(defn get-sub-notification
  "Get subscription notification from Oracle."
  [db subscription-id]
  (let [sql (str "SELECT id, subscription_concept_id, last_notified_at "
                 "FROM cmr_sub_notifications "
                 "WHERE subscription_concept_id = ?")
        results (first (j/query db [sql subscription-id]))]
    (dbresult->sub-notification db results)))

(defn save-sub-notification
  "Create subscription notification record in Oracle."
  [db subscription-id]
  (let [sql (str "INSERT INTO cmr_sub_notifications"
                 "(id, subscription_concept_id)"
                 "VALUES (cmr_sub_notifications_seq.nextval, ?)")]
  (j/db-do-prepared db sql [subscription-id])))

(defn update-sub-notification
  "Update a subscription notification in Oracle."
  [db subscription-id]
  (let [sql (str "UPDATE cmr_sub_notifications "
                 "SET last_notified_at = ? "
                 "WHERE subscription_concept_id = ?")
        now (t/now)]
    (j/db-do-prepared db sql [(cr/to-sql-time now) subscription-id])))

(defn delete-sub-notification
  "Delete a subscription notification record by id"
  [db subscription-id]
  (j/delete! db
             "cmr_sub_notifications"
             ["subscription_concept_id = ?" subscription-id]))

(comment
  (def db (get-in user/system [:apps :metadata-db :db]))

  (println (subscription-exists? db "SUB1234-test"))
  (println (sub-notification-exists? db "SUB1234-test"))

  ;crud tests
  (println (save-sub-notification db "SUB1234-test"))
  (println (sub-notification-exists? db "SUB1234-test"))
  (println (get-sub-notification db "SUB1234-test"))
  (println (update-sub-notification db "SUB1234-test"))
  (println (delete-sub-notification db "SUB1234-test")) )
