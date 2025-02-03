(ns cmr.metadata-db.services.subscriptions
  "Buisness logic for subscription processing."
  (:require
   [cheshire.core :as json]
   [cmr.common.log :refer [debug info]]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.search-service :as mdb-search]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]
   [cmr.transmit.config :as t-config])
  (:import
    (org.apache.commons.validator.routines UrlValidator)))

(def ingest-subscriptions-enabled?
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
  (and ingest-subscriptions-enabled?
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
  "Go through the list of subscriptions and combine the :modes found into one array.
  Return the array of modes."
  [subscriptions]
  (loop [subs subscriptions
         result []]
    (let [sub (first subs)]
      (if (nil? sub)
        result
        (recur (rest subs) (add-to-existing-mode result (get-in sub [:metadata :Mode])))))))


(use 'clojure.set)
(defn create-mode-to-endpoints-map
  "Ex. {
    new: ['sqs:arn:111', 'https://www.url1.com', 'https://www.url2.com'],
    update: ['sqs:arn:111'],
    delete: ['https://www.url1.com']
  }"
  [subscriptions]

  (let [final-map (atom {})]
    (doseq [sub subscriptions]
      (let [temp-map (atom {})
            modes (get-in sub [:metadata :Mode])
            endpoint (get-in sub [:metadata :EndPoint])]
        (doseq [mode modes]
          (swap! temp-map assoc mode #{endpoint})
          ;(println "temp-map so far is " @temp-map)
          )
        ;(println "temp-map at the end is " @temp-map)
        (let [merged-map (merge-with union @final-map @temp-map)]
          ;(println "merged-map is " merged-map)
          (swap! final-map (fn [n] merged-map)))
        )
      ;(println "final-map so far is = " @final-map)
      )
    @final-map
    )
  )

;(defn change-subscription
;  "When a subscription is added or deleted, the collection-concept-id must be put into
;  or deleted from the subscription cache. Get the subscriptions that match the collection-concept-id
;  from the database and rebuild the modes list. Return 1 if successful 0 otherwise."
;  [context concept-edn]
;  (let [coll-concept-id (:CollectionConceptId (:metadata concept-edn))
;        subs (convert-and-filter-subscriptions (get-subscriptions-from-db context coll-concept-id))]
;    (if (seq subs)
;      (subscription-cache/set-value context coll-concept-id (merge-modes subs))
;      (subscription-cache/remove-value context coll-concept-id))))


(defn change-subscription-in-cache
  "When a subscription is added or deleted, the collection-concept-id must be put into
  or deleted from the subscription cache. Get the subscriptions that match the collection-concept-id
  from the database and rebuild the modes list. Return 1 if successful 0 otherwise."
  [context concept-edn]
  (let [coll-concept-id (:CollectionConceptId (:metadata concept-edn))
        subscriptions-found-in-db (convert-and-filter-subscriptions (get-subscriptions-from-db context coll-concept-id))]
    (println "***** subscriptions-found-in-db: " subscriptions-found-in-db)
    ;; if subscriptions found in db, create new cache value and add it to the cache (this may overwrite past cache value)
    (if (seq subscriptions-found-in-db)
      (let [mode-to-endpoints-map (create-mode-to-endpoints-map subscriptions-found-in-db)]
        (subscription-cache/set-value context coll-concept-id mode-to-endpoints-map))
      ;; remove the entire subscription cache record if no active subscriptions for this collection was found in the db
      (subscription-cache/remove-value context coll-concept-id)
      )))

;;
;; The functions below are for subscribing and unsubscribing and endpoint to the topic.
;;

(defn add-or-delete-ingest-subscription-in-cache
  "Do the work to see if subscriptions are enabled and add/remove
  subscription from the cache. Return nil if subscriptions are not
  enabled or the concept converted to edn."
  [context concept]
  (when (and ingest-subscriptions-enabled? (= :subscription (:concept-type concept)))
    (let [concept-edn (convert-concept-to-edn concept)]
      (when (ingest-subscription-concept? concept-edn)
        (change-subscription-in-cache context concept-edn)
        concept-edn))))

(defn- is-valid-sqs-arn
  "Checks if given sqs arn is valid. Returns true or false."
  [endpoint]
  (if endpoint
    (some? (re-matches #"arn:aws:sqs:.*" endpoint))
    false))

(defn- is-valid-subscription-endpoint-url
  "Checks if subscription endpoint destination is a valid url. Returns true or false."
  [endpoint]
  (if endpoint
    (let [default-validator (UrlValidator. UrlValidator/ALLOW_LOCAL_URLS)]
      (.isValid default-validator endpoint))
    false))

(defn- is-local-test-queue
  "Checks if subscription endpoint is a local url that point to the local queue -- this is for local tests.
  Returns true or false."
  [endpoint]
  (if endpoint
    (some? (re-matches #"http://localhost:9324.*" endpoint))
    false))

(defn attach-subscription-to-topic
  "If valid ingest subscription, will attach the subscription concept's sqs arn to external SNS topic
  and will add the sqs arn used as an extra field to the concept"
  [context concept]
    (let [concept-edn (convert-concept-to-edn concept)
          _ (println "concept-edn is " concept-edn)]
      (if (ingest-subscription-concept? concept-edn)
        (let [topic (get-in context [:system :sns :external])
              ;; subscribes the given endpoint sqs arn in the concept to the external SNS topic
               subscription-arn (topic-protocol/subscribe topic concept-edn)]
          (if subscription-arn
              (assoc-in concept [:extra-fields :aws-arn] subscription-arn)
              concept))
        concept)))

(defn set-subscription-arn-if-applicable
  "If subscription has an endpoint that is an SQS ARN, then it will attach the SQS ARN to the CMR SNS external topic and
  save the SQS ARN to the subscription concept.
  Returns the concept with updates or the unchanged concept (if concept is not a subscription concept)"
  [context concept-type concept]
  (if (and ingest-subscriptions-enabled? (= :subscription concept-type))
    (let [concept-edn (convert-concept-to-edn concept)
          endpoint (get-in concept-edn [:metadata :EndPoint])]
      (if (or (is-valid-sqs-arn endpoint) (is-local-test-queue endpoint))
        (attach-subscription-to-topic context concept)
        concept
        ))
    ;; we return concept no matter what because not every concept that enters this func will be a subscription,
    ;; and we don't consider that an error
    concept))

(defn delete-ingest-subscription
  "Remove the subscription from the cache and unsubscribe the subscription from
  the topic if applicable.
  Returns the subscription-arn."
  [context concept]
  (when-let [concept-edn (add-or-delete-ingest-subscription-in-cache context concept)]
    ;; delete ingest subscription sqs attachments
    (when (ingest-subscription-concept? concept-edn)
      (let [topic (get-in context [:system :sns :external])
            subscription-arn (get-in concept-edn [:extra-fields :aws-arn])
            subscription {:concept-id (:concept-id concept-edn)
                          :subscription-arn subscription-arn}]
        (if (or (is-valid-sqs-arn subscription-arn) (is-local-test-queue subscription-arn))
          (topic-protocol/unsubscribe topic subscription)
          subscription-arn)))))

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
  "Go through all the subscriptions and create a map of collection concept ids and
  their mode values. Get the old keys from the cache and if the keys exist in the new structure
  then update the cache with the new values. Otherwise, delete the contents that no longer exists."
  [context]
  (when ingest-subscriptions-enabled?
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

(defn create-notification-message-body
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

;(defn create-attributes-and-subject-map
;  "Determine based on the passed in concept if the granule is new, is an update
;  or delete. Use the passed in mode to determine if any subscription is interested
;  in a notification. If they are then return the message attributes and subject, otherwise
;  return nil."
;  [concept mode coll-concept-id]
;  (cond
;    ;; Mode = Delete.
;    (and (:deleted concept)
;         (some #(= "Delete" %) mode))
;    {:attributes (create-message-attributes coll-concept-id "Delete")
;     :subject (create-message-subject "Delete")}
;
;    ;; Mode = New
;    (and (not (:deleted concept))
;         (= 1 (:revision-id concept))
;         (some #(= "New" %) mode))
;    {:attributes (create-message-attributes coll-concept-id "New")
;     :subject (create-message-subject "New")}
;
;    ;; Mode = Update
;    (and (not (:deleted concept))
;         (pos? (compare (:revision-id concept) 1))
;         (some #(= "Update" %) mode))
;    {:attributes (create-message-attributes coll-concept-id "Update")
;     :subject (create-message-subject "Update")}))

;(defn publish-subscription-notification-if-applicable
;  "Publish a notification to the topic if the passed-in concept is a granule
;  and a subscription is interested in being informed of the granule's actions."
;  [context concept]
;  (when (granule-concept? (:concept-type concept))
;    (let [start (System/currentTimeMillis)
;          coll-concept-id (:parent-collection-id (:extra-fields concept))
;          sub-cache-map (subscription-cache/get-value context coll-concept-id)]
;      ;; if this granule's collection is found in subscription cache that means it has a subscription attached to it
;      (when sub-cache-map
;        ;; Check the mode to see if the granule notification needs to be pushed. Mode examples are 'new', 'update', 'delete'.
;        (let [topic (get-in context [:system :sns :internal])
;              message (create-notification-message-body concept)
;              ;; TODO Jyna will need to update this attributes and subject map for URL endpoints
;              {:keys [attributes subject]} (create-attributes-and-subject-map concept sub-cache-map coll-concept-id)]
;          (when (and attributes subject)
;            (let [result (topic-protocol/publish topic message attributes subject)
;                  duration (- (System/currentTimeMillis) start)]
;              (debug (format "Work potential subscription publish took %d ms." duration))
;              result)))))))


(defn- get-gran-concept-mode
  "Gets the granule concept's ingestion mode (i.e. Update, Delete, New, etc)"
  [concept]
  (cond
    (:deleted concept) "Delete"
    ;; Mode = New
    (and (not (:deleted concept)) (= 1 (:revision-id concept))) "New"
    ;; Mode = Update
    (and (not (:deleted concept)) (pos? (compare (:revision-id concept) 1))) "Update"
    ))

(defn- create-message-attributes-map
  "Create message attribute map that SQS uses to filter out messages from the SNS topic."
  [endpoint mode coll-concept-id]
  (cond
    (or (is-valid-sqs-arn endpoint) (is-local-test-queue endpoint)) (cond
                                  (= "Delete" mode) {:attributes (create-message-attributes coll-concept-id "Delete")}
                                  (= "New" mode) {:attributes (create-message-attributes coll-concept-id "New")}
                                  (= "Update" mode) {:attributes (create-message-attributes coll-concept-id "Update")})
    (is-valid-subscription-endpoint-url endpoint) {:attributes {"endpoint" endpoint
                                                                "endpoint-type" "url"
                                                                "mode" mode}}))

(defn publish-subscription-notification-if-applicable
  "Publish a notification to the topic if the passed-in concept is a granule
  and a subscription is interested in being informed of the granule's actions."
  [context concept]
  (println "***** INSIDE publish-subscription-notification-if-applicable")
  (when (granule-concept? (:concept-type concept))
    (let [start (System/currentTimeMillis)
          coll-concept-id (:parent-collection-id (:extra-fields concept))
          _ (println "coll-concept-id = " coll-concept-id)
          sub-cache-map (subscription-cache/get-value context coll-concept-id)]
      ;; if this granule's collection is found in subscription cache that means it has a subscription attached to it
      (when sub-cache-map
        (let [gran-concept-mode (get-gran-concept-mode concept)
              _ (println "gran-concept-mode = " gran-concept-mode)
              endpoint-list (get sub-cache-map gran-concept-mode)
              _ (println "endpoint-list = " endpoint-list)]
          ;; for every endpoint in the list create a attributes/subject map and send it along its way
          (doseq [endpoint endpoint-list]
            (let [topic (get-in context [:system :sns :internal])
                  coll-concept-id (:parent-collection-id (:extra-fields concept))
                  message (create-notification-message-body concept)
                  message-attributes-map (create-message-attributes-map endpoint gran-concept-mode coll-concept-id)
                  _ (println "message-attributes-map = " message-attributes-map)
                  subject-map {:subject (create-message-subject gran-concept-mode)}
                  _ (println "subject map = " subject-map)]
              (when (and message-attributes-map subject-map)
                (let [result (topic-protocol/publish topic message message-attributes-map subject-map)
                      duration (- (System/currentTimeMillis) start)]
                  (debug (format "Subscription publish for endpoint %s took %d ms." endpoint duration))
                  result)))
            ))))))

(comment
  (let [system (get-in user/system [:apps :metadata-db])]
    (refresh-subscription-cache {:system system})))
