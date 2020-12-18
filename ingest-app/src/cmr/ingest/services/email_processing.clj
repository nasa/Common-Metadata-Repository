(ns cmr.ingest.services.email-processing
  "This contains the helper functions for the subscription processing job."
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as cr]
   [clj-time.core :as t]
   [clojure.spec.alpha :as spec]
   [clojure.string :as string]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.time-keeper :as tk]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as config]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.search :as search]
   [markdown.core :as markdown]
   [postal.core :as postal-core]))

;; Call the following to trigger a job, example below will fire an email subscription
;; UPDATE QRTZ_TRIGGERS
;; SET NEXT_FIRE_TIME =(((cast (SYS_EXTRACT_UTC(SYSTIMESTAMP) as DATE) - DATE'1970-01-01')*86400 + 1200) * 1000)
;; WHERE trigger_name='EmailSubscriptionProcessing.job.trigger';

;; Specs =============================================================
(def date-rx "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

(def time-constraint-pattern (re-pattern (str date-rx "," date-rx)))

(spec/def ::time-constraint (spec/and
                              string?
                              #(re-matches time-constraint-pattern %)))

(defconfig email-subscription-processing-lookback
  "Number of seconds to look back for granule changes."
  {:default 3600
   :type Long})

(defconfig email-server-host
  "The host name for email server."
  {:default ""
   :type String})

(defconfig email-server-port
  "The port number for email server."
  {:default 25
   :type Long})

(defconfig mail-sender
  "The email sender's email address."
  {:default ""
   :type String})

(def subscription-permission-notification-expiration-days
  "The number of days to wait before deleting subscription and notifying user that their permission
   to view the collection was removed"
   3)

(defn- create-query-params
  "Create query parameters using the query string like
  \"polygon=1,2,3&concept-id=G1-PROV1\""
  [query-string]
  (let [query-string-list (string/split query-string #"&")
        query-map-list (map #(let [a (string/split % #"=")]
                               {(first a) (second a)})
                             query-string-list)]
     (apply merge query-map-list)))

(defn email-granule-url-list
 "take a list of URLs and format them for an email"
 [gran-ref-location]
 (string/join "\n" (map #(str "* [" % "](" % ")") gran-ref-location)))

(defn failed-permission-content
  "Create an email body for failed permission notifications."
  [from-email-address to-email-address coll-id]
  {:from from-email-address
   :to to-email-address
   :subject "You can no longer view this collection"
   :body [{:type "text/html"
           :content (markdown/md-to-html-string
                     (str "You are currently subscribed to receive updates for new data added to " coll-id ".\n\n"
                          "Because you no longer have access to view this collection, you will not receive notifications "
                          "related to this collection.\n\n"
                          "If you believe this subscription was deleted in error, please contact us at [cmr-support@earthdata.nasa.gov](mailto:cmr-support@earthdata.nasa.gov).\n\n"))}]})

(defn create-email-content
 "Create an email body for subscriptions"
 [from-email-address to-email-address gran-ref-location subscription]
 (let [metadata (json/parse-string (:metadata subscription))
       concept-id (get-in subscription [:extra-fields :collection-concept-id])
       meta-query (get metadata "Query")
       sub-start-time (:start-time subscription)]
   {:from from-email-address
    :to to-email-address
    :subject "Email Subscription Notification"
    :body [{:type "text/html"
            :content (markdown/md-to-html-string
                      (str "You have subscribed to receive notifications when data is added to the following query:\n\n"
                           "`" concept-id "`\n\n"
                           "`" meta-query "`\n\n"
                           "Since this query was last run at "
                           sub-start-time
                           ", the following granules have been added or updated:\n\n"
                           (email-granule-url-list gran-ref-location)
                           "\n\nTo unsubscribe from these notifications, or if you have any questions, please contact us at [cmr-support@earthdata.nasa.gov](mailto:cmr-support@earthdata.nasa.gov).\n"))}]}))

(defn- add-updated-since
  "Pull out the start and end times from a time-constraint value and associate them to a map"
  [raw time-constraint]
  (let [parts (clojure.string/split time-constraint, #",")
        start-time (first parts)
        end-time (last parts)]
    (assoc raw :start-time start-time :end-time end-time)))

(defn- send-update-subscription-notification-time!
  "handle any packaging of data here before sending it off to transmit package"
  [context data]
  (debug "send-update-subscription-notification-time with" data)
  (search/save-subscription-notification-time context data))

(defn- check-collection-permissions
  "Checks subscribered read permission for collection, we will send a notification if they lose visability."
  [context coll-id subscriber-id permission-check-time permission-check-failed]
  (let [permissions (json/parse-string
                     (access-control/get-permissions context {:user_id subscriber-id
                                                              :concept_id coll-id}))
        has-permission (some #{"read"} (get permissions coll-id))]
    (if (and (not has-permission)
             permission-check-failed)
      [permission-check-failed permission-check-time]
      [(not has-permission) nil])))

(defn- filter-gran-refs-by-subscriber-id
  "Takes a list of granule references and a subscriber id and removes any granule that the user does
   not have read access to."
  [context gran-refs subscriber-id]
  (when (seq gran-refs)
    (let [permissions (json/parse-string
                       (access-control/get-permissions context {:user_id subscriber-id
                                                                :concept_id (map :concept-id gran-refs)}))]
      (filter (fn [granule-reference]
                (some #{"read"} (get permissions (:concept-id granule-reference))))
              gran-refs))))

(defn- subscription->time-constraint
  "Create a time-constraint from a subscriptions last-notified-at value or amount-in-sec from the end."
  [subscription end amount-in-sec]
  {:post [(spec/valid? ::time-constraint %)]}
  (let [begin (if-let [start (get-in subscription [:extra-fields :last-notified-at])]
                start
                (t/minus end (t/seconds amount-in-sec)))]
    (str begin "," end)))

(defn- search-gran-refs-by-collection-id
  [context params1 params2 sub-id]
  (try
    (let [gran-refs1 (search/find-granule-references context params1)
          gran-refs2 (search/find-granule-references context params2)]
      (distinct (concat gran-refs1 gran-refs2)))
    (catch Exception e
      (error "Exception caught processing subscription" sub-id " searching with params "
             (dissoc params1 :token) (dissoc params2 :token) "\n\n" (.getMessage e) "\n\n" e)
      [])))

(defn- process-subscriptions
  "Process each subscription in subscriptions into tuple for testing purposes and to use as
   the input when sending the subscription emails."
  [context subscriptions]
  (for [raw-subscription subscriptions
        :let [time-constraint (subscription->time-constraint
                               raw-subscription
                               (tk/now)
                               (email-subscription-processing-lookback))
              subscription (add-updated-since raw-subscription time-constraint)
              subscriber-id (get-in subscription [:extra-fields :subscriber-id])
              email-address (get-in subscription [:extra-fields :email-address])
              sub-id (get subscription :concept-id)
              coll-id (get-in subscription [:extra-fields :collection-concept-id])
              native-id (get subscription :native-id)
              provider-id (get subscription :provider-id)
              [permission-check-failed permission-check-time] (check-collection-permissions
                                                               context
                                                               coll-id
                                                               subscriber-id
                                                               (get-in subscription [:extra-fields :permission-check-time])
                                                               (get-in subscription [:extra-fields :permission-check-failed]))
              query-string (-> (:metadata subscription)
                               (json/decode true)
                               :Query)
              query-params (create-query-params query-string)
              params1 (merge {:created-at time-constraint
                              :collection-concept-id coll-id
                              :token (config/echo-system-token)}
                             query-params)
              params2 (merge {:revision-date time-constraint
                              :collection-concept-id coll-id
                              :token (config/echo-system-token)}
                             query-params)
              gran-refs (search-gran-refs-by-collection-id context params1 params2 sub-id)
              subscriber-filtered-gran-refs (filter-gran-refs-by-subscriber-id context gran-refs subscriber-id)]]
    (do
      (send-update-subscription-notification-time! context {:subscription-concept-id sub-id
                                                            :permission-check-failed permission-check-failed
                                                            :permission-check-time permission-check-time})
      [sub-id coll-id native-id provider-id subscriber-filtered-gran-refs
       email-address subscription permission-check-time
       permission-check-failed])))

(defn- send-subscription-emails
  "Takes processed subscription tuples and sends out emails if applicable."
  [context subscriber-filtered-gran-refs-list]
  (doseq [subscriber-filtered-gran-refs-tuple subscriber-filtered-gran-refs-list
          :let [[sub-id coll-id native-id provider-id subscriber-filtered-gran-refs
                 email-address subscription permission-check-time
                 permission-check-failed] subscriber-filtered-gran-refs-tuple
                email-settings {:host (email-server-host) :port (email-server-port)}]]
    (if (and (t/after? (t/minus (tk/now) (t/days subscription-permission-notification-expiration-days))
                       (or (when permission-check-time
                             (cr/from-string permission-check-time))
                           (tk/now)))
             permission-check-failed)
      (try
        (ingest/delete-concept
         context
         {:provider-id provider-id
          :native-id native-id
          :concept-type :subscription})
        (postal-core/send-message email-settings (failed-permission-content (mail-sender)
                                                                            email-address
                                                                            coll-id))
        (catch Exception e
          (error "Exception caught in sending failed permissions email for subscription: " sub-id "\n\n"
                 (.getMessage e) "\n\n" e)))
      (when (seq subscriber-filtered-gran-refs)
        (let [gran-ref-locations (map :location subscriber-filtered-gran-refs)
              email-content (create-email-content (mail-sender) email-address gran-ref-locations subscription)]
          (try
            (postal-core/send-message email-settings email-content)
            (catch Exception e
              (error "Exception caught in email subscription: " sub-id "\n\n"
                     (.getMessage e) "\n\n" e))))))))

(defn email-subscription-processing
  "Process email subscriptions and send email when found granules matching the collection and queries
  in the subscription and were created/updated during the last processing interval."
  [context]
  (let [subscriptions (->> (mdb/find-concepts context {:latest true} :subscription)
                           (remove :deleted)
                           (map #(select-keys % [:concept-id :extra-fields :metadata :provider-id :native-id])))]
    (send-subscription-emails context (process-subscriptions context subscriptions))))
