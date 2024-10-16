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

(defn remove-from-existing-mode
  "Depending on the passed in new-mode ['New' 'Update'] remove from the structure
  {Collection concept id: {\"New\" 1
                           \"Update\" 1}}
   either the count or the mode if count is 0."
  [existing-mode new-mode]
  (loop [ms new-mode
         result existing-mode]
    (let [mode (first ms)
          mode-count (if (and result
                              (result mode))
                       (result mode) 0)
          compare-to-one (compare mode-count 1)]
      (if mode
        (when (seq result)
          (if (or (= 0 compare-to-one)
                  (= -1 compare-to-one))
            (recur (rest ms) (dissoc result mode))
            (recur (rest ms) (assoc result mode (dec mode-count)))))
        result))))

(defn delete-subscription 
  "When a subscription is deleted, the collection-concept-id must be removed 
  from the subscription cache. Decrement the count if more than 1 subscription
  uses the collection-concept-id."
  [context concept-type revisioned-tombstone]
  (when (subscription-concept? concept-type revisioned-tombstone)
    (let [coll-concept-id (:collection-concept-id (:extra-fields revisioned-tombstone))
          existing-value (subscription-cache/get-value context coll-concept-id)
          mode (:mode (:extra-fields revisioned-tombstone))
          new-mode (remove-from-existing-mode existing-value mode)]
      (if (seq new-mode)
        (subscription-cache/set-value context coll-concept-id new-mode)
        (subscription-cache/remove-value context coll-concept-id)))))

(defn add-to-existing-mode
  "Depending on the passed in new-mode ['New' 'Update'] create a structure that looks like
  {Collection concept id: {\"New\" 1
                           \"Update\" 1}}"
  [existing-mode new-mode]
  (loop [ms new-mode
         result existing-mode]
    (let [mode (first ms)
          mode-count (if (and result
                              (result mode))
                       (result mode)
                       0)]
      (if mode
        (if result
          (recur (rest ms) (assoc result mode (inc mode-count)))
          (recur (rest ms) (assoc {} mode (inc mode-count))))
        result))))

(defn add-subscription
  "When a subscription is added, the collection-concept-id must be put into  
  the subscription cache. If the collection-concept-id already exists then another
  subscription is also using it so increment the count. The count is used to determine
  whether or not the entry can be deleted. The modes are concatenated so that the user
  of the cache knows which modes to key off of."
  [context concept-type concept]
  (when (subscription-concept? concept-type concept)
    (let [coll-concept-id (:collection-concept-id (:extra-fields concept))
          mode (:mode (:extra-fields concept))
          existing-mode (subscription-cache/get-value context coll-concept-id)
          new-value (add-to-existing-mode existing-mode mode)]
      (subscription-cache/set-value context coll-concept-id new-value))))

(defn create-subscription-cache-contents-for-refresh
  "Go through all of the subscriptions and find the ones that are 
  ingest subscriptions. Create the mode values for each collection-concept-id
  and put those into a map. The end result looks like:
  {Collection concept id 1: {\"New\" 1
                           \"Update\" 1}
   Collection concept id 2: {\"New\" 2
                           \"Update\" 1
                           \"Delete\" 3}
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
          (assoc concept-map coll-concept-id (add-to-existing-mode nil mode))))
      result)))

(defn ^:dynamic get-subscriptions-from-db
  "Get the subscriptions from the database. This function primarily exists so that
  it can be stubbed out for unit tests."
  [context]
  (mdb-search/find-concepts context {:latest true
                                     :concept-type :subscription}))

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
