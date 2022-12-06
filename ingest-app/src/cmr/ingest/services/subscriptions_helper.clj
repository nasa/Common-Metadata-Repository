(ns cmr.ingest.services.subscriptions-helper
  "This contains the code for the scheduled subscription job."
  (:require
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.spec.alpha :as spec]
   [clojure.string :as string]
   [cmr.common-app.config :as common-app-config]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.date-time-range-parser :as date-time-range-parser]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors] 
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as config]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.search :as search]
   [cmr.transmit.urs :as urs]
   [markdown.core :as markdown]
   [postal.core :as postal-core]))

;; Specs =============================================================
(def date-rx "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

(def time-constraint-pattern (re-pattern (str date-rx "," date-rx)))

(spec/def ::time-constraint (spec/and
                             string?
                             #(re-matches time-constraint-pattern %)))

;; Call the following to trigger a job, example below will fire an email subscription
;; UPDATE QRTZ_TRIGGERS
;; SET NEXT_FIRE_TIME =(((cast (SYS_EXTRACT_UTC(SYSTIMESTAMP) as DATE) - DATE'1970-01-01')*86400 + 1200) * 1000)
;; WHERE trigger_name='EmailSubscriptionProcessing.job.trigger';

(defn create-query-params
  "Create query parameters using the query string like
  \"polygon=1,2,3&concept-id=G1-PROV1\""
  [query-string]
  (when query-string
    (let [query-string-list (string/split query-string #"&")
          query-map-list (map #(let [a (string/split % #"=")]
                                 {(first a) (second a)})
                              query-string-list)]
      (apply merge query-map-list))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Jobs for refreshing the collection granule aggregation cache in the indexer. This is a singleton job
;; and the indexer does not have a database so it's triggered from Ingest and sent via message.
;; Only one node needs to refresh the cache because we're using the  fallback cache with Redis cache.
;; The value stored in Redis will be available to all the nodes.

(defconfig email-server-host
  "The host name for email server."
  {:default ""
   :type String})

(defconfig email-server-port
  "The port number for email server."
  {:default 25
   :type Long})

(defconfig subscriptions-limit
  "The subscription limit for a single non-admin subscriber id."
  {:default 100
   :type Long})

(defconfig mail-sender
  "The email sender's email address."
  {:default ""
   :type String})

(defconfig email-subscription-processing-interval
  "Number of seconds between jobs processing email subscriptions."
  {:default 3600
   :type Long})

(defconfig email-subscription-processing-lookback
  "Number of seconds to look back for granule changes."
  {:default 3600
   :type Long})

(defconfig subscription-management-page
  "Link to subscription management page"
  {:default "https://search.earthdata.nasa.gov/subscriptions"
   :type String})

(defn email-url-list
  "take a list of URLs and format them for an email"
  [concept-ref-locations]
  (string/join "\n" (map #(str "* [" % "](" % ")") concept-ref-locations)))

(defn- add-updated-since
  "Pull out the start and end times from a time-constraint value and associate them to a map"
  [raw time-constraint]
  (let [parts (string/split time-constraint, #",")
        start-time (first parts)
        end-time (last parts)]
    (assoc raw :start-time start-time :end-time end-time)))

(defn- send-update-subscription-notification-time!
  "Fires off an http call to update the time which the subscription last was processed"
  [context sub-id]
  (debug "send-update-subscription-notification-time with" sub-id)
  (search/save-subscription-notification-time context sub-id))

(defn- filter-concept-refs-by-subscriber-id
  "Takes a list of concept references and a subscriber id and removes any concept that the user does
   not have read access to."
  [context concept-refs subscriber-id]
  (when (seq concept-refs)
    (let [permissions (json/parse-string
                       (access-control/get-permissions context {:user_id subscriber-id
                                                                :concept_id (map :concept-id concept-refs)}))]
      (filter (fn [concept-reference]
                (some #{"read"} (get permissions (:concept-id concept-reference))))
              concept-refs))))

(defn- subscription->time-constraint
  "Create a time-constraint from a subscriptions last-notified-at value or amount-in-sec from the end."
  [subscription end amount-in-sec]
  (let [begin (if-let [start (get-in subscription [:extra-fields :last-notified-at])]
                start
                (t/minus end (t/seconds amount-in-sec)))]
    (str begin "," end)))

(defn- search-gran-refs-by-collection-id
  [context params sub-id]
  (try
    (search/find-concept-references context params :granule)
    (catch Exception e
      (error "Exception caught processing subscription" sub-id " searching with params "
             (dissoc params :token) "\n\n" (.getMessage e) "\n\n" e)
      [])))

(defn- search-collection-refs
  [context params sub-id]
  (try
    (search/find-concept-references context params :collection)
    (catch Exception e
      (error "Exception caught processing subscription" sub-id " searching with params "
             (dissoc params :token) "\n\n" (.getMessage e) "\n\n" e)
      [])))

(defn- process-subscriptions
  "Process each subscription in subscriptions into tuple for testing purposes and to use as
   the input when sending the subscription emails."
  [context subscriptions revision-date-range]
  (for [raw-subscription subscriptions
        :let [time-constraint (if revision-date-range
                                revision-date-range
                                (subscription->time-constraint
                                 raw-subscription
                                 (t/now)
                                 (email-subscription-processing-lookback)))
              subscription (add-updated-since raw-subscription time-constraint)
              {:keys [concept-id extra-fields metadata]} subscription
              {:keys [subscriber-id subscription-type collection-concept-id]} extra-fields
              sub-type (or (keyword subscription-type) :granule)
              sub-meta (json/decode metadata true)
              query-string (:Query sub-meta)
              query-params (create-query-params query-string)
              search-by-revision (merge {:revision-date time-constraint}
                                        (when collection-concept-id
                                          {:collection-concept-id collection-concept-id})
                                        {:token (config/echo-system-token)}
                                        query-params)
              concept-refs (if (= :collection sub-type)
                             (search-collection-refs context search-by-revision concept-id)
                             (search-gran-refs-by-collection-id context search-by-revision concept-id))
              subscriber-filtered-concept-refs (filter-concept-refs-by-subscriber-id context concept-refs subscriber-id)]]
    [concept-id subscriber-filtered-concept-refs subscriber-id sub-type subscription]))

(defn- ^:redef send-email
  "Wrapper for postal-core/send-message"
  [email-settings email-content]
  (postal-core/send-message email-settings email-content))

(defmulti create-email-content
  (fn [sub-type from-email-address to-email-address concept-ref-locations subscription]
    sub-type))

(defmethod create-email-content :granule
  [sub-type from-email-address to-email-address concept-ref-locations subscription]
  (let [metadata (json/parse-string (:metadata subscription))
        concept-id (get-in subscription [:extra-fields :collection-concept-id])
        meta-query (get metadata "Query")
        sub-start-time (:start-time subscription)
        sub-end-time (:end-time subscription)]
    {:from from-email-address
     :to to-email-address
     :subject "Email Subscription Notification"
     :body [{:type "text/html"
             :content (markdown/md-to-html-string
                       (str "You have subscribed to receive notifications when data is added to the following query:\n\n"
                            "`" concept-id "`\n\n"
                            "`" meta-query "`\n\n"
                            "Running the query with a time window from " sub-start-time " to " sub-end-time
                            ", the following granules have been added or updated:\n\n"
                            (email-url-list concept-ref-locations)
                            "\n\nTo unsubscribe from these notifications, go to "
                            (subscription-management-page)
                            ".\n\nIf you have any questions, please contact us at ["
                            (common-app-config/cmr-support-email)
                            "](mailto:"
                            (common-app-config/cmr-support-email)
                            ").\n"))}]}))

(defmethod create-email-content :collection
  [sub-type from-email-address to-email-address concept-ref-locations subscription]
  (let [metadata (json/parse-string (:metadata subscription))
        meta-query (get metadata "Query")
        sub-start-time (:start-time subscription)
        sub-end-time (:end-time subscription)]
    {:from from-email-address
     :to to-email-address
     :subject "Email Subscription Notification"
     :body [{:type "text/html"
             :content (markdown/md-to-html-string
                       (str "You have subscribed to receive notifications when new collections are added that match the following search query:\n\n"
                            "`" meta-query "`\n\n"
                            "Running the query with a time window from " sub-start-time " to " sub-end-time
                            ", the following collections have been added or updated:\n\n"
                            (email-url-list concept-ref-locations)
                            "\n\nTo unsubscribe from these notifications, go to "
                            (subscription-management-page)
                            ".\n\nIf you have any questions, please contact us at ["
                            (common-app-config/cmr-support-email)
                            "](mailto:"
                            (common-app-config/cmr-support-email)
                            ").\n"))}]}))

(defn- send-subscription-emails
  "Takes processed processed subscription tuples and sends out emails if applicable. If update-notification-time?
   is true, the subscription last-notified-at value will be updated in metadata-db."
  ([context subscriber-filtered-concept-refs-list]
   (send-subscription-emails context subscriber-filtered-concept-refs-list true))
  ([context subscriber-filtered-concept-refs-list update-notification-time?]
   (doseq [subscriber-filtered-concept-refs-tuple subscriber-filtered-concept-refs-list
           :let [[sub-id subscriber-filtered-concept-refs subscriber-id sub-type subscription] subscriber-filtered-concept-refs-tuple]]
     (when (seq subscriber-filtered-concept-refs)
       (let [concept-ref-locations (map :location subscriber-filtered-concept-refs)
             email-address (urs/get-user-email context subscriber-id)
             email-content (create-email-content sub-type (mail-sender) email-address concept-ref-locations subscription)
             email-settings {:host (email-server-host) :port (email-server-port)}]
         (try
           (send-email email-settings email-content)
           (info (str "Successfully processed subscription [" sub-id "].
                      Sent subscription email to [" email-address "].
                      \nSubscription email contents: [" email-content "]."))
           (when update-notification-time?
             (send-update-subscription-notification-time! context sub-id))
           (catch Exception e
             (error "Exception caught in email subscription: " sub-id "\n\n"
                    (.getMessage e) "\n\n" e))))))
   subscriber-filtered-concept-refs-list))

(defn email-subscription-processing
  "Process email subscriptions and send email when found granules matching the collection and queries
  in the subscription and were created/updated during the last processing interval."
  ([context]
   (email-subscription-processing context nil))
  ([context revision-date-range]
   (let [subscriptions (->> (mdb/find-concepts context {:latest true} :subscription)
                            (remove :deleted)
                            (map #(select-keys % [:concept-id :extra-fields :metadata])))]
     (send-subscription-emails context (process-subscriptions context subscriptions revision-date-range) (nil? revision-date-range)))))

(defn- validate-revision-date-range
  "Throws service errors on invalid revision date ranges"
  [revision-date-range]
  (let [{:keys [start-date end-date]} (date-time-range-parser/parse-datetime-range revision-date-range)]
    (when-not start-date
      (errors/throw-service-error :bad-request "Missing start date in revision-date-range."))
    (when-not end-date
      (errors/throw-service-error :bad-request "Missing end date in revision-date-range."))
    (when-not (t/before? start-date end-date)
      (errors/throw-service-error :bad-request "The start date should occur before the end date."))))

(defn trigger-email-subscription-processing
  "Trigger subscription processing given the provided revision-date.  Revision date is passed through to
   a Search query and should follow the same format as the CMR Search API."
  [context params]
  (let [revision-date-range (:revision-date-range params)]
    (validate-revision-date-range revision-date-range)
    (email-subscription-processing context revision-date-range)))
