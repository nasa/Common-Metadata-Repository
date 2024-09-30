(ns cmr.metadata-db.services.subscriptions
  "Buisness logic for subscription processing."
  (:require
   [cmr.common.log :refer [info]]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]))

(def subscriptions-enabled?
  "Checks to see if ingest subscriptions are enabled."
  (mdb-config/ingest-subscription-enabled))

(defn subscription-concept?
  "Checks to see if the passed in concept-type and concept is a subscription concept."
  [concept-type concept]
  (info (format "Ingest subscriptions subscription-concept? concept-type: %s concept: %s" concept-type concept))
  (and subscriptions-enabled?
       (= :subscription concept-type)
       (some? (:endpoint (:extra-fields concept)))))

(defn granule-concept?
  "Checks to see if the passed in concept-type and concept is a granule concept."
  [concept-type]
  (and subscriptions-enabled?
       (= :granule concept-type)))

(defn delete-subscription 
  "When a subscription is deleted, the collection-concept-id must be removed 
  from the subscription cache."
  [context concept-type revisioned-tombstone]
  (info (format "Ingest subscriptions delete-subscription subscription-concept?: %s" (subscription-concept? concept-type revisioned-tombstone)))
  (when (subscription-concept? concept-type revisioned-tombstone)
    (let [coll-concept-id (:collection-concept-id (:extra-fields revisioned-tombstone))
          _ (info (format "Ingest subscriptions delete-subscription collection-concept-id: [%s]" coll-concept-id))
          value (subscription-cache/remove-value context coll-concept-id)
          _ (info (format "Ingest subscriptions delete-subscription Remove worked or not: %d" value))]
      value)))

(defn add-subscription
  "When a subscription is added, the collection-concept-id must be put into  
  the subscription cache."
  [context concept-type concept]
  (when (subscription-concept? concept-type concept)
    (let [coll-concept-id (:collection-concept-id (:extra-fields concept))
          mode (:mode (:extra-fields concept))]
      (subscription-cache/set-value context coll-concept-id {"enabled" true
                                                             "mode" mode}))))
