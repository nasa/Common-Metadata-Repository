(ns cmr.ingest.services.nrt-subscription.subscriptions
  "Business logic for subscription processing."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.log :refer [debug info]]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol]
   [cmr.ingest.config :as ingest-config]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.ingest.services.nrt-subscription.subscription-cache :as subscription-cache]
   [cmr.transmit.config :as t-config])
  (:import
    (org.apache.commons.validator.routines UrlValidator)))

(def ingest-subscriptions-enabled?
  "Checks to see if ingest subscriptions are enabled."
  (ingest-config/ingest-subscription-enabled))

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
   (let [response (mdb2/find-concepts context
                                      {}
                                      :subscription
                                      {:raw? true
                                       :http-options {:query-params
                                                      {:latest true
                                                       :concept-type :subscription
                                                       :subscription-type "granule"}}})]
     (if (= (:status response) 200)
       (:body response)
       nil)))
  ([context coll-concept-id]
   (let [response (mdb2/find-concepts context
                                      {}
                                      :subscription
                                      {:raw? true
                                       :http-options {:query-params
                                                      {:collection-concept-id coll-concept-id
                                                       :latest true
                                                       :concept-type :subscription}}})]
     (if (= (:status response) 200)
       (:body response)
       nil))))

(defn convert-concept-to-edn
  "Converts the passed in concept to edn"
  [subscription]
  (update subscription :metadata #(json/decode % true)))

(comment
  (println subscriptions)
  )
(defn convert-and-filter-subscriptions
  "Convert the metadata of the subscriptions to edn and then filter out the non
  ingest subscriptions."
  [subscriptions]
  (def subscriptions subscriptions)
  (let [subs (map convert-concept-to-edn subscriptions)]
    (filter #(and (ingest-subscription-concept? %)
                  (= false (:deleted %)))
            subs)))

(use 'clojure.set)
(defn create-mode-to-endpoints-map
  "Creates a list of endpoint sets associated to a mode. For each ingest subscription,
  an endpoint set is created consisting first of the subscription's endpoint followed
  by the subscriber-id. This set is then put on one or more lists that is associated
  to each mode described in the subscription. The mode lists are merged together per
  each collection concept id that exist in all of the ingest subscriptions.
  This function returns a map in this structure:
  {
    new: [['sqs:arn:111', 'user1'], ['https://www.url1.com', 'user2'], ['https://www.url2.com', 'user3']],
    update: [['sqs:arn:111', 'user1']],
    delete: [['https://www.url1.com', 'user2']]
  }

  This structure is used for fast lookups by mode. For a mode, each endpoint set is iterated over
  using the subscriber id to check if the subscriber has read access. If they do then a notification
  is sent to the endpoint."
  [subscriptions-of-same-collection]

  (let [final-map (atom {})]
    (doseq [sub subscriptions-of-same-collection]
      (let [temp-map (atom {})
            modes (get-in sub [:metadata :Mode])
            endpoint (get-in sub [:metadata :EndPoint])
            subscriber (get-in sub [:metadata :SubscriberId])]
        (doseq [mode modes]
          (swap! temp-map assoc mode #{[endpoint, subscriber]}))
        (let [merged-map (merge-with union @final-map @temp-map)]
          (swap! final-map (fn [n] merged-map)))))
    @final-map))

(defn change-subscription-in-cache
  "When a subscription is added or deleted, the collection-concept-id must be added or deleted from the subscription cache.
  Get the subscriptions that match the collection-concept-id from the database and rebuild the info map for this collection.
  Return 1 if successful 0 otherwise."
  [context concept-edn]
  (let [coll-concept-id (:CollectionConceptId (:metadata concept-edn))
        subscriptions-found-in-db (convert-and-filter-subscriptions (get-subscriptions-from-db context coll-concept-id))]
    ;; if subscriptions found in db, create new cache value and add it to the cache (this may overwrite past cache value)
    (if (seq subscriptions-found-in-db)
      (let [mode-to-endpoints-map (create-mode-to-endpoints-map subscriptions-found-in-db)]
        (subscription-cache/set-value context coll-concept-id {"Mode" mode-to-endpoints-map}))
      ;; remove the entire subscription cache record if no active subscriptions for this collection was found in the db
      (subscription-cache/remove-value context coll-concept-id))))

;;
;; The functions below are for subscribing and unsubscribing an endpoint to the topic.
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
    (let [concept-edn (convert-concept-to-edn concept)]
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
  (def context context)
  (def concept-type concept-type)
  (def concept concept)
  (if (and ingest-subscriptions-enabled? (= :subscription concept-type))
    (let [concept-edn (convert-concept-to-edn concept)
          endpoint (get-in concept-edn [:metadata :EndPoint])]
      (if (or (is-valid-sqs-arn endpoint) (is-local-test-queue endpoint))
        (attach-subscription-to-topic context concept)
        concept))
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
            endpoint (get-in concept-edn [:metadata :EndPoint])
            subscription-arn (get-in concept-edn [:extra-fields :aws-arn])
            subscription {:concept-id (:concept-id concept-edn)
                          :subscription-arn subscription-arn}]
        (if (or (is-valid-sqs-arn endpoint) (is-local-test-queue endpoint))
          (topic-protocol/unsubscribe topic subscription)
          subscription-arn)))))

;;
;; The functions below are for refreshing the subscription cache if needed.
;;

(defn create-subscription-cache-contents-for-refresh
    "Go through all the subscriptions and find the ones that are
    ingest subscriptions. Create the mode values for each collection-concept-id
    and put those into a map. The end result looks like:
    { coll_1: {
                Mode: {
                        New: #([URL1, user1], [SQS1, user2]),
                        Update: #([URL2, user3])
                       }
               },
      coll_2: {...}"
    [subscriptions]
  (let [cache-map (atom {})
        coll-to-subscription-concept-map (atom {})]
    ;; order the subscriptions by collection and create a map collection to subscriptions
    (doseq [sub subscriptions]
      (let [metadata-edn (:metadata sub)
            coll-concept-id  (:CollectionConceptId metadata-edn)
            sub-list (get @coll-to-subscription-concept-map coll-concept-id)]
            (swap! coll-to-subscription-concept-map conj {coll-concept-id (conj sub-list sub)})))
    ;;for every subscription list by the collection create a mode-to-endpoints-map and add it to the final cache map
    (doseq [[coll-id subscription-list] @coll-to-subscription-concept-map]
      (let [mode-to-endpoints-map (create-mode-to-endpoints-map subscription-list)]
        (swap! cache-map assoc coll-id {"Mode" mode-to-endpoints-map})))
    @cache-map))

(defn refresh-subscription-cache
  "Go through all the subscriptions and create a map of collection concept ids and
  their mode values. Get the old keys from the cache and if the keys exist in the new structure
  then update the cache with the new values. Otherwise, delete the contents that no longer exists."
  [context]
  (when ingest-subscriptions-enabled?
    (info "Starting refreshing the ingest subscription cache.")
    (let [subscriptions-from-db (convert-and-filter-subscriptions (get-subscriptions-from-db context))
          new-contents (create-subscription-cache-contents-for-refresh subscriptions-from-db)
          cache-content-keys (subscription-cache/get-keys context)]
      ;; update the cache with the new values contained in the new-contents map.
      (doall (map #(subscription-cache/set-value context % (new-contents %)) (keys new-contents)))
      ;; Go through and remove any cache items that are not in the new-contents map.
      (doall (map #(when-not (new-contents %)
                     (subscription-cache/remove-value context %))
                  cache-content-keys))
      (info "Finished refreshing the ingest subscription cache.")
      {:status 200})))

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

(def public-search-url
  "Creates a public search URL from the ingest URL parts. The passed in
  ingest-public-url-map contains the following:
   {:protocol (ingest-public-protocol)
    :host (ingest-public-host)
    :port (ingest-public-port)
    :relative-root-url (transmit-config/ingest-relative-root-url)}
  Use the above information and replace the relative-root-url with search if
  if the context of ingest exists.
  The public search URL is put into the granule notification message so
  so the end user knows how to retrieve the granule."
  (memoize
   (fn [ingest-public-url-map]
     (let [url (t-config/format-public-root-url ingest-public-url-map)
           search-url (string/replace-first url "ingest" "search")]
       (if (string/includes? search-url "3002")
         (string/replace-first search-url "3002" "3003")
         search-url)))))

(defn get-location-message-str
  "Get the granule search location for the subscription notification message."
  [context concept]
  (def context context)
  (def concept concept)
  (let [public-search-url (public-search-url (get-in context [:system :public-conf]))]
    (str "\"location\": \""
         (format "%sconcepts/%s/%s"
                 public-search-url
                 (:concept-id concept)
                 (:revision-id concept))
         "\"")))

(defn create-notification-message-body
  "Create the notification when a subscription exists.
   * At this time concept-id, revision-id, granule-ur, and location are all that is needed of the
     granule is needed as an external subscription process will pull other values meaning this code
     will not need to translate the XML or JSON to find values for the subscription process.
   * This function exists so that it can be tested as the output is expected in external software:
     'subscription_worker'
   * Returns a String containing JSON."
  [context concept]
  (let [pgi-str (get-producer-granule-id-message-str concept)
        granule-ur-str (get-in concept [:extra-fields :granule-ur])
        concept-id-str (:concept-id concept)
        revision-id-str (get concept :revision-id "1")
        location-str (get-location-message-str context concept)]
    (if pgi-str
      (format "{\"concept-id\": \"%s\", \"revision-id\": \"%s\", \"granule-ur\": \"%s\", %s, %s}"
              concept-id-str revision-id-str granule-ur-str location-str, pgi-str)
      (format "{\"concept-id\": \"%s\", \"revision-id\": \"%s\", \"granule-ur\": \"%s\", %s}"
              concept-id-str revision-id-str granule-ur-str location-str))))

(defn create-message-attributes
  "Create the notification message attributes so that the notifications can be
  filtered to the correct subscribing endpoint."
  [collection-concept-id mode subscriber]
  {"collection-concept-id" collection-concept-id
   "mode" mode
   "subscriber" subscriber})

(defn create-message-subject
  "Creates the message subject."
  [mode]
  (str mode " Notification"))

(defn- get-gran-concept-mode
  "Gets the granule concept's ingestion mode (i.e. Update, Delete, New, etc)"
  [concept]
  (cond
    ;; Mode = Delete
    (:deleted concept) "Delete"
    ;; Mode = New
    (and (not (:deleted concept)) (= 1 (:revision-id concept))) "New"
    ;; Mode = Update
    (and (not (:deleted concept)) (pos? (compare (:revision-id concept) 1))) "Update"))

(defn- create-message-attributes-map
  "Create message attribute map that SQS uses to filter out messages from the SNS topic."
  [endpoint-set mode coll-concept-id]
  (let [endpoint (first endpoint-set)
        subscriber (second endpoint-set)]
    (cond
      (or (is-valid-sqs-arn endpoint)
          (is-local-test-queue endpoint)) (cond
                                            (= "Delete" mode) (create-message-attributes coll-concept-id "Delete" subscriber)
                                            (= "New" mode) (create-message-attributes coll-concept-id "New" subscriber)
                                            (= "Update" mode) (create-message-attributes coll-concept-id "Update" subscriber))
      (is-valid-subscription-endpoint-url endpoint) {"endpoint" endpoint
                                                     "endpoint-type" "url"
                                                     "mode" mode
                                                     "subscriber" subscriber
                                                     "collection-concept-id" coll-concept-id})))

(defn publish-subscription-notification-if-applicable
  "Publish a notification to the topic if the passed-in concept is a granule
  and a subscription is interested in being informed of the granule's actions."
  [context concept]
  (when (granule-concept? (:concept-type concept))
    (let [start (System/currentTimeMillis)
          coll-concept-id (:parent-collection-id (:extra-fields concept))
          sub-cache-map (subscription-cache/get-value context coll-concept-id)]
      ;; if this granule's collection is found in subscription cache that means it has a subscription attached to it
      (when sub-cache-map
        (let [gran-concept-mode (get-gran-concept-mode concept)
              endpoint-list (get-in sub-cache-map ["Mode" gran-concept-mode])
              result-array (atom [])]
          ;; for every endpoint in the list create an attributes/subject map and send it along its way
          (doseq [endpoint-set endpoint-list]
            (let [topic (get-in context [:system :sns :internal])
                  coll-concept-id (:parent-collection-id (:extra-fields concept))
                  message (create-notification-message-body context concept)
                  message-attributes-map (create-message-attributes-map endpoint-set gran-concept-mode coll-concept-id)
                  subject (create-message-subject gran-concept-mode)]
             (when (and message-attributes-map subject)
                (let [result (topic-protocol/publish topic message message-attributes-map subject)
                      duration (- (System/currentTimeMillis) start)]
                  (debug (format "Subscription publish for endpoint %s took %d ms." (first endpoint-set) duration))
                  (swap! result-array (fn [_n] (conj @result-array result)))))))
          @result-array)))))

(comment
  (let [system (get-in user/system [:apps :ingest])]
    (refresh-subscription-cache {:system system})))
