(ns cmr.metadata-db.services.subscriptions
  "Buisness logic for subscription processing."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.search-service :as mdb-search]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]))

(def subscriptions-enabled?
  "Checks to see if ingest subscriptions are enabled."
  (mdb-config/ingest-subscription-enabled))

(defn subscription-concept?
  "Checks to see if the passed in concept-type and concept is a subscription concept."
  [concept-type concept]
  (and subscriptions-enabled?
       (= :subscription concept-type)
       (some? (:endpoint (:extra-fields concept)))
       (= "ingest" (:method (:extra-fields concept)))))

(defn granule-concept?
  "Checks to see if the passed in concept-type and concept is a granule concept."
  [concept-type]
  (and subscriptions-enabled?
       (= :granule concept-type)))

(defn ^:dynamic get-subscriptions-from-db
  "Get the subscriptions from the database. This function primarily exists so that
  it can be stubbed out for unit tests."
  ([context]
   (mdb-search/find-concepts context {:latest true
                                      :concept-type :subscription
                                      :subscription-type "granule"}))
  ([context coll-concept-id]
   (mdb-search/find-concepts context {:latest true
                                      :concept-type :subscription
                                      :collection-concept-id coll-concept-id})))

(defn add-to-existing-mode
  "Depending on the passed in new-mode [\"New\" \"Update\"] create a structure that merges
  the new mode to the existing mode. The result looks like [\"New\" \"Update\"]"
  [existing-modes new-modes]
  (loop [ms new-modes
         result existing-modes]
    (let [mode (first ms)]
      (if (nil? mode)
        result
        (if result
          (if (some #(= mode %) result)
            (recur (rest ms) result)
            (recur (rest ms) (merge result mode)))
          (recur (rest ms) [mode]))))))

(defn merge-modes
  "Go through the list of subscriptions to see if any exist that match the
  passed in collection-concept-id. Return true if any subscription exists
  otherwise return false."
  [subscriptions]
  (loop [subs subscriptions
         result []]
    (let [sub (first subs)]
      (if (nil? sub)
        result
        (recur (rest subs) (add-to-existing-mode result (get-in sub [:extra-fields :mode])))))))

(defn change-subscription
  "When a subscription is added or deleted, the collection-concept-id must be put into
  or deleted from the subscription cache. Get the subscriptions that match the collection-concept-id
  from the database and rebuild the modes list."
  [context concept-type concept]
  (when (subscription-concept? concept-type concept)
    (let [coll-concept-id (:collection-concept-id (:extra-fields concept))
          subs (filter #(subscription-concept? (get % :concept-type) %)
                       (get-subscriptions-from-db context coll-concept-id))]
      (if (seq subs)
        (subscription-cache/set-value context coll-concept-id (merge-modes subs))
        (subscription-cache/remove-value context coll-concept-id)))))

(defn create-subscription-cache-contents-for-refresh
  "Go through all of the subscriptions and find the ones that are 
  ingest subscriptions. Create the mode values for each collection-concept-id
  and put those into a map. The end result looks like:
  {Collection concept id 1: [\"New\" \"Update\"]
   Collection concept id 2: [\"New\" \"Update\" \"Delete\"]
   ...}"
  [result sub]
  (let [metadata (:metadata sub)
        metadata-edn (json/decode metadata true)]
    (if (and (some? (:EndPoint metadata-edn))
             (= "ingest" (:Method metadata-edn)))
      (let [coll-concept-id (:collection-concept-id (:extra-fields sub))
            concept-map (result coll-concept-id)
            mode (:Mode metadata-edn)]
        (if concept-map
          (update result coll-concept-id #(add-to-existing-mode % mode))
          (assoc result coll-concept-id (add-to-existing-mode nil mode))))
      result)))

(defn refresh-subscription-cache
  "Go through all of the subscriptions and create a map of collection concept ids and
  their mode values. Get the old keys from the cache and if the keys exist in the new structure
  then update the cache with the new values. Otherwise delete the contents that no longer exists."
  [context]
  (when subscriptions-enabled?
    (let [subs (get-subscriptions-from-db context)
          new-contents (reduce create-subscription-cache-contents-for-refresh {} subs)
          cache-content-keys (subscription-cache/get-keys context)]
      ;; update the cache with the new values contained in the new-contents map.
      (doall (map #(subscription-cache/set-value context % (new-contents %)) (keys new-contents)))
      ;; Go through and remove any cache items that are not in the new-contents map.
      (doall (map #(when-not (new-contents %)
                     (subscription-cache/remove-value context %))
                  cache-content-keys)))))

(comment
  (let [system (get-in user/system [:apps :metadata-db])]
    (refresh-subscription-cache {:system system})))
