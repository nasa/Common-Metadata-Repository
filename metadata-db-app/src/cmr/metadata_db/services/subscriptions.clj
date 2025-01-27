(ns cmr.metadata-db.services.subscriptions
  "Buisness logic for subscription processing."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [cmr.common.log :refer [debug info]]
   [cmr.common.services.errors :as errors]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.search-service :as mdb-search]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]
   [cmr.transmit.config :as t-config])
  (:import
    (org.apache.commons.validator.routines UrlValidator)))

(def subscriptions-enabled?
  "Checks to see if ingest subscriptions are enabled."
  (mdb-config/ingest-subscription-enabled))

(defn ingest-subscription-concept?
  "Checks to see if the concept is a ingest subscription concept."
  [concept-edn]
  (let [metadata (:metadata concept-edn)]
    (and (some? (:EndPoint metadata))
         (= "ingest" (:Method metadata)))))

(defn granule-concept?
  "Checks to see if the passed in concept-type and concept is a granule concept."
  [concept-type]
  (and subscriptions-enabled?
       (= :granule concept-type)))

;;
;; The functions below are for adding and deleting subscriptions to the cache.
;;

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

(defn convert-concept-to-edn
  "Converts the passed in concept to edn"
  [subscription]
  (update subscription :metadata #(json/decode % true)))

(defn convert-and-filter-subscriptions
  "Convert the metadata of the subscriptions to edn and then filter out the non
  ingest subscriptions."
  [subscriptions]
  (let [subs (map convert-concept-to-edn subscriptions)]
    (filter #(and (ingest-subscription-concept? %)
                  (= false (:deleted %)))
            subs)))

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
        (recur (rest subs) (add-to-existing-mode result (get-in sub [:metadata :Mode])))))))

(defn change-subscription
  "When a subscription is added or deleted, the collection-concept-id must be put into
  or deleted from the subscription cache. Get the subscriptions that match the collection-concept-id
  from the database and rebuild the modes list. Return 1 if successful 0 otherwise."
  [context concept-edn]
  (let [coll-concept-id (:CollectionConceptId (:metadata concept-edn))
        subs (convert-and-filter-subscriptions (get-subscriptions-from-db context coll-concept-id))]
    (if (seq subs)
      (subscription-cache/set-value context coll-concept-id (merge-modes subs))
      (subscription-cache/remove-value context coll-concept-id))))

;;
;; The functions below are for subscribing and unsubscribing
;;

(defn add-delete-subscription
  "Do the work to see if subscriptions are enabled and add/remove
  subscription from the cache. Return nil if subscriptions are not
  enabled or the concept converted to edn."
  [context concept]
  (when (and subscriptions-enabled?
             (= :subscription (:concept-type concept)))
    (let [concept-edn (convert-concept-to-edn concept)]
      (when (ingest-subscription-concept? concept-edn)
        (change-subscription context concept-edn)
        concept-edn))))

(defn- is-valid-sqs-arn
  "Checks if given sqs arn is valid. Returns true or false."
  [endpoint]
  (some? (re-matches #"arn:aws:sqs:.*" endpoint)))

(defn- is-valid-subscription-endpoint-url
  "Checks if subscription endpoint destination is a valid url. Returns true or false."
  [endpoint]
  (let [default-validator (UrlValidator. UrlValidator/ALLOW_LOCAL_URLS)]
    (.isValid default-validator endpoint)))

(defn- send-sub-to-url-dest
  "Sends subscription details to url given. Throws error if subscription is not successful."
  [subscription-concept dest-endpoint]
  (let [response (client/post dest-endpoint
                              {:body (json/generate-string subscription-concept)
                               :content-type "application/json"
                               :throw-exceptions false})]
    (when-not (= 200 (:status response))
      (throw (Exception. (format "Failed to send subscription message to url %s. Response was: %s" dest-endpoint response))))))

(defn add-subscription
  "Add the subscription to the cache and subscribe the subscription to
  the topic."
  [context concept]
  (when-let [concept-edn (convert-concept-to-edn concept)]
    (when (ingest-subscription-concept? concept-edn)
      (let [endpoint (:EndPoint (:metadata concept-edn))]
        (cond
          (is-valid-sqs-arn endpoint) (let [topic (get-in context [:system :sns :external])]
                                        (topic-protocol/subscribe topic concept-edn))
          (is-valid-subscription-endpoint-url endpoint) (send-sub-to-url-dest concept-edn endpoint)
          :else (errors/throw-service-error :bad-request
                                            (format "Endpoint given for subscription was neither a valid sqs arn or a valid URL.
                                            Invalid endpoint received was: %s" endpoint))
          )))))

(defn delete-subscription
  "Remove the subscription from the cache and unsubscribe the subscription from
  the topic."
  [context concept]
  (when-let [concept-edn (add-delete-subscription context concept)]
    (when (ingest-subscription-concept? concept-edn)
      (let [endpoint (:EndPoint (:metadata concept-edn))]
        (cond
          (is-valid-sqs-arn endpoint) (let [topic (get-in context [:system :sns :external])]
                                        (topic-protocol/unsubscribe topic {:concept-id (:concept-id concept-edn)
                                                                           :subscription-arn (get-in concept-edn [:extra-fields :aws-arn])}))
          (is-valid-subscription-endpoint-url endpoint) (send-sub-to-url-dest concept-edn endpoint)
          :else (errors/throw-service-error :bad-request
                                            (format "Endpoint given for subscription was neither a valid sqs arn or a valid URL.
                                            Invalid endpoint received was: %s" endpoint)))
        ))))

;;
;; The functions below are for refreshing the subscription cache if needed.
;;

(defn create-subscription-cache-contents-for-refresh
  "Go through all the subscriptions and find the ones that are
  ingest subscriptions. Create the mode values for each collection-concept-id
  and put those into a map. The end result looks like:
  {Collection concept id 1: [\"New\" \"Update\"]
   Collection concept id 2: [\"New\" \"Update\" \"Delete\"]
   ...}"
  [result sub]
  (let [concept-edn (convert-concept-to-edn sub)
        metadata-edn (:metadata concept-edn)]
    (if (ingest-subscription-concept? concept-edn)
      (let [coll-concept-id (:CollectionConceptId metadata-edn)
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
    (info "Starting refreshing the ingest subscription cache.")
    (let [subs (get-subscriptions-from-db context)
          new-contents (reduce create-subscription-cache-contents-for-refresh {} subs)
          cache-content-keys (subscription-cache/get-keys context)]
      ;; update the cache with the new values contained in the new-contents map.
      (doall (map #(subscription-cache/set-value context % (new-contents %)) (keys new-contents)))
      ;; Go through and remove any cache items that are not in the new-contents map.
      (doall (map #(when-not (new-contents %)
                     (subscription-cache/remove-value context %))
                  cache-content-keys))
      (info "Finished refreshing the ingest subscription cache."))))

;;
;; The functions below are for publishing messages to the topic.
;;

(defn get-producer-granule-id-message-str
  "Get the granule producer id from the metadata and create a string for the
  subscription notification message."
  [concept-edn]
  (let [identifiers (get-in concept-edn [:metadata :DataGranule :Identifiers])
        pgi (when identifiers
              (:Identifier (first
                            (filter #(= "ProducerGranuleId" (:IdentifierType %))
                                    identifiers))))]
    (when pgi
      (str "\"producer-granule-id\": \"" pgi "\""))))

(defn get-location-message-str
  "Get the granule search location for the subscription notification message."
  [concept]
  (str "\"location\": \""
       (format "%sconcepts/%s/%s"
               (t-config/format-public-root-url (:search (t-config/app-conn-info)))
               (:concept-id concept)
               (:revision-id concept))
       "\""))

(defn create-notification
  "Create the notification when a subscription exists. Returns either a notification message or nil."
  [concept]
  (let [concept-edn (convert-concept-to-edn concept)
        pgi-str (get-producer-granule-id-message-str concept-edn)
        granule-ur-str (str "\"granule-ur\": \"" (get-in concept-edn [:metadata :GranuleUR]) "\"")
        g-concept-id-str (str "\"concept-id\": \"" (:concept-id concept-edn) "\"")
        location-str (get-location-message-str concept)]
    (str "{" g-concept-id-str ", " granule-ur-str ", " (when pgi-str (str pgi-str ", ")) location-str "}")))

(defn create-message-attributes
  "Create the notification message attributes so that the notifications can be
  filtered to the correct subscribing endpoint."
  [collection-concept-id mode]
  {"collection-concept-id" collection-concept-id
   "mode" mode})

(defn create-message-subject
  "Creates the message subject."
  [mode]
  (str mode " Notification"))

(defn get-attributes-and-subject
  "Determine based on the passed in concept if the granule is new, is an update
  or a delete. Use the passed in mode to determine if any subscription is interested
  in a notification. If they are then return the message attributes and subject, otherwise
  return nil."
  [concept mode coll-concept-id]
  (cond
    ;; Mode = Delete.
    (and (:deleted concept)
         (some #(= "Delete" %) mode))
    {:attributes (create-message-attributes coll-concept-id "Delete")
     :subject (create-message-subject "Delete")}

    ;; Mode = New
    (and (not (:deleted concept))
         (= 1 (:revision-id concept))
         (some #(= "New" %) mode))
    {:attributes (create-message-attributes coll-concept-id "New")
     :subject (create-message-subject "New")}

    ;; Mode = Update
    (and (not (:deleted concept))
         (pos? (compare (:revision-id concept) 1))
         (some #(= "Update" %) mode))
    {:attributes (create-message-attributes coll-concept-id "Update")
     :subject (create-message-subject "Update")}))

(defn work-potential-notification
  "Publish a notification to the topic if the passed in concept is a granule
  and a subscription is interested in being informed."
  [context concept]
  (when (granule-concept? (:concept-type concept))
    (let [start (System/currentTimeMillis)
          coll-concept-id (:parent-collection-id (:extra-fields concept))
          sub-cache-map (subscription-cache/get-value context coll-concept-id)]
      (when sub-cache-map
        ;; Check the mode to see if the granule notification needs to be pushed.
        (let [topic (get-in context [:system :sns :internal])
              message (create-notification concept)
              {:keys [attributes subject]} (get-attributes-and-subject concept sub-cache-map coll-concept-id)]
          (when (and attributes subject)
            (let [result (topic-protocol/publish topic message attributes subject)
                  duration (- (System/currentTimeMillis) start)]
              (debug (format "Work potential subscription publish took %d ms." duration))
              result)))))))

(comment
  (let [system (get-in user/system [:apps :metadata-db])]
    (refresh-subscription-cache {:system system})))
