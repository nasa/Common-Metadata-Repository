(ns cmr.metadata-db.test.services.subscriptions-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.util :refer [are3]]
   [cmr.message-queue.config :as msg-config]
   [cmr.message-queue.pub-sub :as pub-sub]
   [cmr.message-queue.test.test-util :as sqs-test-util]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]
   [cmr.metadata-db.services.subscriptions :as subscriptions] 
   [cmr.redis-utils.test.test-util :as redis-test-util]
   [cmr.message-queue.queue.aws-queue :as queue]))

(use-fixtures :once (join-fixtures [redis-test-util/embedded-redis-server-fixture
                                    sqs-test-util/embedded-sqs-server-fixture]))

(defn create-value-set
  "Take a map result set and turn the vector values into set values."
  [cache-map]
  (when-let [map-keys (keys cache-map)]
    (reduce (fn [result k]
              (into result
                    {k (set (get cache-map k))})) {} map-keys)))

#_{:clj-kondo/ignore [:unresolved-var]}
(deftest subscription-cache-test
  (let [cache-key subscription-cache/subscription-cache-key
        test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}}}
        cache-client (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache-client cache-key)
    (testing "Cache is empty"
      (is (nil? (hash-cache/get-map cache-client cache-key))))
    (testing "Testing if cache is enabled."
      (let [value (mdb-config/ingest-subscription-enabled)]
        (is (= value subscriptions/ingest-subscriptions-enabled?))))
    (testing "Testing if a passed in concept is a subscription concept"
      (is (subscriptions/ingest-subscription-concept? {:concept-type :subscription
                                                       :deleted false
                                                       :metadata {:CollectionConceptId "C12345-PROV1"
                                                                  :EndPoint "some-endpoint"
                                                                  :Method "ingest"}
                                                       :extra-fields {:collection-concept-id "C12345-PROV1"}})))
    (testing "Testing if a passed in concept is a granule concept"
      (is (subscriptions/granule-concept? :granule)))
    (testing "Add a subscription to the cache"
      (with-bindings {#'subscriptions/get-subscriptions-from-db
                      (fn [_context _coll-concept-id] '({:concept-type :subscription
                                                         :deleted false
                                                         :metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                                                                     \"EndPoint\":\"some-endpoint\",
                                                                     \"Mode\":[\"New\"],
                                                                     \"Method\":\"ingest\",
                                                                     \"SubscriberId\":\"user1\"}",
                                                         :extra-fields {:collection-concept-id "C12345-PROV1"}}))}
        (is (= 1 (subscriptions/change-subscription-in-cache test-context {:concept-type :subscription
                                                                           :deleted false
                                                                           :metadata {:CollectionConceptId "C12345-PROV1"
                                                                                      :EndPoint "some-endpoint"
                                                                                      :Mode ["New"]
                                                                                      :Method "ingest"
                                                                                      :SubscriberId "user1"}
                                                                           :extra-fields {:collection-concept-id "C12345-PROV1"}})))))
    (testing "Delete a subscription from the cache"
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] '({:concept-type :subscription
                                                                                                   :deleted true
                                                                                                   :metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                                                                                                               \"EndPoint\":\"some-endpoint\",
                                                                                                               \"Mode\":[\"New\"],
                                                                                                               \"Method\":\"ingest\",
                                                                                                               \"SubscriberId\":\"user1\"}",
                                                                                                   :extra-fields {:collection-concept-id "C12345-PROV1"}}))}
        (is (= 1 (subscriptions/change-subscription-in-cache test-context {:concept-type :subscription
                                                                           :deleted true
                                                                           :metadata {:CollectionConceptId "C12345-PROV1"
                                                                                      :EndPoint "some-endpoint"
                                                                                      :Mode ["New"]
                                                                                      :Method "ingest"
                                                                                      :SubscriberId "user1"}
                                                                           :extra-fields {:collection-concept-id "C12345-PROV1"}})))))

    (testing "adding and removing subscriptions from the cache."
      (are3
       [expected example-record db-contents]
       (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-contents)}
         (subscriptions/change-subscription-in-cache test-context example-record)
         (is (= expected (hash-cache/get-map cache-client cache-key))))


       "Adding 1 subscription"
       {"C12345-PROV1" {"Mode" {"Update" (set [["some-endpoint" "user1"]])}}}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"Update\"],
                      \"Method\":\"ingest\",
                      \"SubscriberId\":\"user1\"}",
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Adding duplicate subscription"
       {"C12345-PROV1" {"Mode" {"Update" (set [["some-endpoint" "user1"]])}}}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"Update\"],
                      \"Method\":\"ingest\",
                      \"SubscriberId\":\"user1\"}",
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Adding override subscription"
       {"C12345-PROV1" {"Mode" {"New" (set [["some-endpoint" "user1"]])
                        "Delete" (set [["some-endpoint" "user1"]])}}}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\",
                      \"SubscriberId\":\"user1\"}"
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Adding new subscription that matches the one from before."
       {"C12345-PROV1" {"Mode" {"New" (set [["some-endpoint" "user1"]])
                        "Delete" (set [["some-endpoint" "user1"]])}}
        "C12346-PROV1" {"Mode" {"New" (set [["some-endpoint" "user1"]])
                        "Delete" (set [["some-endpoint" "user1"]])}}}
       {:metadata {:CollectionConceptId "C12346-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12346-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\",
                      \"SubscriberId\":\"user1\"}",
          :extra-fields {:collection-concept-id "C12346-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Removing 1 subscription"
       {"C12346-PROV1" {"Mode" {"New" (set [["some-endpoint" "user1"]])
                        "Delete" (set [["some-endpoint" "user1"]])}}}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       ;; even though C12346-PROV1 is in the db, we are search only for
       ;; concepts with the collection-concept-id.
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\",
                      \"SubscriberId\":\"user1\"}"
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted true
          :concept-type :subscription})

       "Removing same subscription"
       {"C12346-PROV1" {"Mode" {"New" (set [["some-endpoint" "user1"]])
                        "Delete" (set [["some-endpoint" "user1"]])}}}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\",
                      \"SubscriberId\":\"user1\"}"
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted true
          :concept-type :subscription})

       "Removing last subscription"
       nil
       {:metadata {:CollectionConceptId "C12346-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12346-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\",
                      \"SubscriberId\":\"user1\"}",
          :extra-fields {:collection-concept-id "C12346-PROV1"}
          :deleted true
          :concept-type :subscription})

       "Try to remove something that doesn't exist"
       nil
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '()))

    (testing "adding and deleting subscriptions from the cache calling add-delete-subscription"
      (let [db-contents '()]
        (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-contents)}
          (is (= {:metadata {:CollectionConceptId "C12345-PROV1"
                             :EndPoint "ARN"
                             :Mode ["New" "Delete"]
                             :Method "ingest"
                             :SubscriberId "user1"}
                  :concept-type :subscription}
                 (subscriptions/add-or-delete-ingest-subscription-in-cache
                  test-context
                  {:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                               \"EndPoint\":\"ARN\",
                               \"Mode\":[\"New\", \"Delete\"],
                               \"Method\":\"ingest\",
                               \"SubscriberId\":\"user1\"}"
                   :concept-type :subscription}))))))))

(def db-result-1
  '({:revision-id 1
     :deleted false
     :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
     :provider-id "PROV1"
     :user-id "ECHO_SYS"
     :transaction-id "2000000009M"
     :native-id "erichs_ingest_subscription"
     :concept-id "SUB1200000005-PROV1"
     :metadata "{\"SubscriberId\":\"user1\",
                 \"CollectionConceptId\":\"C1200000002-PROV1\",
                 \"EndPoint\":\"some-endpoint\",
                 \"Mode\":[\"New\",\"Delete\"],
                 \"Method\":\"ingest\",
                 \"EmailAddress\":\"erich.e.reiter@nasa.gov\",
                 \"Query\":\"collection-concept-id=C1200000002-PROV1\",
                 \"Name\":\"Ingest-Subscription-Test\",
                 \"Type\":\"granule\",
                 \"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1\",\"Name\":\"UMM-Sub\",\"Version\":\"1.1.1\"}}"
     :revision-date "2024-10-07T18:13:32.608Z"
     :extra-fields {:normalized-query "76c6d7a828ef81efb3720638f335f65c"
                    :subscription-type "granule"
                    :subscription-name "Ingest-Subscription-Test"
                    :subscriber-id "user1"
                    :collection-concept-id "C1200000002-PROV1"}
     :concept-type :subscription}))

(def db-result-2
  '({:revision-id 1
     :deleted false
     :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
     :provider-id "PROV1"
     :user-id "ECHO_SYS"
     :transaction-id "2000000010M"
     :native-id "erichs_ingest_subscription2"
     :concept-id "SUB1200000006-PROV1"
     :metadata "{\"SubscriberId\":\"user1\",
                 \"CollectionConceptId\":\"C12346-PROV1\",
                 \"EndPoint\":\"some-endpoint\",
                 \"Mode\":[\"New\",\"Update\"],
                 \"Method\":\"ingest\",
                 \"EmailAddress\":\"erich.e.reiter@nasa.gov\",
                 \"Query\":\"collection-concept-id=C12346-PROV1\",
                 \"Name\":\"Ingest-Subscription-Test\",
                 \"Type\":\"granule\",
                 \"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1\",\"Name\":\"UMM-Sub\",\"Version\":\"1.1.1\"}}"
     :revision-date "2024-10-07T18:13:32.608Z"
     :extra-fields {:normalized-query "76c6d7a828ef81efb3720638f335f65c"
                    :subscription-type "granule"
                    :subscription-name "Ingest-Subscription-Test"
                    :subscriber-id "user1"
                    :collection-concept-id "C12346-PROV1"}
     :concept-type :subscription}))

(def db-result-2-updated
  '({:revision-id 1
     :deleted false
     :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
     :provider-id "PROV1"
     :user-id "ECHO_SYS"
     :transaction-id "2000000010M"
     :native-id "erichs_ingest_subscription2"
     :concept-id "SUB1200000006-PROV1"
     :metadata "{\"SubscriberId\":\"user1\",
                 \"CollectionConceptId\":\"C12346-PROV1\",
                 \"EndPoint\":\"some-endpoint-2\",
                 \"Mode\":[\"New\"],
                 \"Method\":\"ingest\",
                 \"EmailAddress\":\"erich.e.reiter@nasa.gov\",
                 \"Query\":\"collection-concept-id=C12346-PROV1\",
                 \"Name\":\"Ingest-Subscription-Test\",
                 \"Type\":\"granule\",
                 \"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1\",\"Name\":\"UMM-Sub\",\"Version\":\"1.1.1\"}}"
     :revision-date "2024-10-07T18:13:32.608Z"
     :extra-fields {:normalized-query "76c6d7a828ef81efb3720638f335f65c"
                    :subscription-type "granule"
                    :subscription-name "Ingest-Subscription-Test"
                    :subscriber-id "user1"
                    :collection-concept-id "C12346-PROV1"}
     :concept-type :subscription}))

(def db-result-3
 (concat db-result-1
         db-result-2-updated
         '({:revision-id 1
            :deleted false
            :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
            :provider-id "PROV1"
            :user-id "ECHO_SYS"
            :transaction-id "2000000011M"
            :native-id "erichs_ingest_subscription3"
            :concept-id "SUB1200000008-PROV1"
            :metadata "{\"SubscriberId\":\"user1\",
                        \"CollectionConceptId\":\"C1200000002-PROV1\",
                        \"EndPoint\":\"some-endpoint\",
                        \"Mode\":[\"Update\"],
                        \"Method\":\"ingest\",
                        \"EmailAddress\":\"erich.e.reiter@nasa.gov\",
                        \"Query\":\"collection-concept-id=C1200000002-PROV1\",
                        \"Name\":\"Ingest-Subscription-Test\",
                        \"Type\":\"granule\",
                        \"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1\",\"Name\":\"UMM-Sub\",\"Version\":\"1.1.1\"}}"
            :revision-date "2024-10-07T18:13:32.608Z"
            :extra-fields {:normalized-query "76c6d7a828ef81efb3720638f335f65c"
                           :subscription-type "granule"
                           :subscription-name "Ingest-Subscription-Test"
                           :subscriber-id "user1"
                           :collection-concept-id "C1200000002-PROV1"}
            :concept-type :subscription})))

(def db-result-4
  '({:revision-id 1
     :deleted false
     :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
     :provider-id "PROV1"
     :user-id "ECHO_SYS"
     :transaction-id "20000000013M"
     :native-id "erichs_ingest_subscription9"
     :concept-id "SUB1200000009-PROV1"
     :metadata "{\"SubscriberId\":\"user1\",
                 \"CollectionConceptId\":\"C1200000003-PROV1\",
                 \"EndPoint\":\"some-endpoint\",
                 \"Mode\":[\"New\",\"Delete\"],
                 \"Method\":\"ingest\",
                 \"EmailAddress\":\"erich.e.reiter@nasa.gov\",
                 \"Query\":\"collection-concept-id=C1200000003-PROV1\",
                 \"Name\":\"Ingest-Subscription-Test\",
                 \"Type\":\"granule\",
                 \"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1\",\"Name\":\"UMM-Sub\",\"Version\":\"1.1.1\"}}"
     :revision-date "2024-10-07T18:13:32.608Z"
     :extra-fields {:normalized-query "76c6d7a828ef81efb3720638f335f65c"
                    :subscription-type "granule"
                    :subscription-name "Ingest-Subscription-Test"
                    :subscriber-id "user1"
                    :collection-concept-id "C1200000003-PROV1"}
     :concept-type :subscription}))

(deftest subscription-refresh-cache-test
  (let [cache-key subscription-cache/subscription-cache-key
        test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}}}
        cache-client (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache-client cache-key)
    (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result-1)}
      (subscriptions/change-subscription-in-cache test-context {:metadata {:CollectionConceptId "C1200000002-PROV1"}}))
    (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result-2)}
      (subscriptions/change-subscription-in-cache test-context {:metadata {:CollectionConceptId "C12346-PROV1"}}))
    (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result-4)}
      (subscriptions/change-subscription-in-cache test-context {:metadata {:CollectionConceptId "C1200000003-PROV1"}}))
    (testing "What is in the cache"
      (is (= {"C1200000002-PROV1" {"Mode"
                                   {"New" (set [["some-endpoint" "user1"]])
                                    "Delete" (set [["some-endpoint" "user1"]])}}
              "C12346-PROV1" {"Mode"
                              {"New" (set [["some-endpoint" "user1"]])
                               "Update" (set [["some-endpoint" "user1"]])}}
              "C1200000003-PROV1" {"Mode"
                                   {"New" (set [["some-endpoint" "user1"]])
                                    "Delete" (set [["some-endpoint" "user1"]])}}}
             (hash-cache/get-map cache-client cache-key))))
    (testing "Cache needs to be updated."
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context] db-result-3)}
        (subscriptions/refresh-subscription-cache test-context))
      ;; Update includes C1200000003-PROV1 was completely removed, and C1200000002-PROV1 mode Update was added and C12346-PROV1 lost its Update Mode and New Mode has a new endpoint
      (is (= {"C1200000002-PROV1" {"Mode"
                                   {"New" (set [["some-endpoint" "user1"]])
                                    "Update" (set [["some-endpoint" "user1"]])
                                    "Delete" (set [["some-endpoint" "user1"]])}}
              "C12346-PROV1" {"Mode"
                              {"New" (set [["some-endpoint-2" "user1"]])}}}
             (hash-cache/get-map cache-client cache-key))))
    (testing "Testing no subscriptions"
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context] '())}
        (subscriptions/refresh-subscription-cache test-context))
      (is (nil? (hash-cache/get-map cache-client cache-key))))
    ))

(deftest get-producer-granule-id-message-str-test
  (testing "Getting producer granule id for the subscription notification message"
    (let [concept-edn {:metadata {:DataGranule {:Identifiers [{:IdentifierType "ProducerGranuleId"
                                                               :Identifier "Algorithm-1"}]}}}]
      (is (= "\"producer-granule-id\": \"Algorithm-1\""
          (subscriptions/get-producer-granule-id-message-str concept-edn)))))
  (testing "Getting producer granule id that doesn't exist. "
    (let [concept-edn {:metadata {:DataGranule {:Identifiers [{:IdentifierType "SomeOtherID"
                                                               :Identifier "Algorithm-1"}]}}}]
      (is (= nil (subscriptions/get-producer-granule-id-message-str concept-edn))))))

(deftest get-location-message-str-test
  (testing "Getting location url for the concept id."
    (let [concept {:concept-id "G12345-PROV1"
                   :revision-id 1}]
      (is (= "\"location\": \"http://localhost:3003/concepts/G12345-PROV1/1\""
             (subscriptions/get-location-message-str concept))))))

;; The output of the function being tested is needed and expected for external process
;; 'subscription_worker'
(deftest create-notification-test
  (testing "Getting the notification for a concept."
    (let [expected (str "{\"concept-id\": \"G12345-PROV1\"}")
          concept {:concept-id "G12345-PROV1"
                   :revision-id 1
                   :metadata "{\"GranuleUR\": \"GranuleUR\",
                               \"DataGranule\": {\"Identifiers\": [{\"IdentifierType\": \"ProducerGranuleId\",
                                                                    \"Identifier\": \"Algorithm-1\"}]}}"}
          xml-concept {:concept-id "G12345-PROV1"
                       :revision-id 1
                       :metadata
                       "<Granule>
                           <GranuleUR>
                             S1A_S3_SLC__1SDH_20140615T034742_20140615T034807_001055_00107C_9928-SLC
                           </GranuleUR>
                           <DataGranule>
                             <ProducerGranuleId>
                               S1A_S3_SLC__1SDH_20140615T034742_20140615T034807_001055_00107C_9928
                             </ProducerGranuleId>
                           </DataGranule>
                         </Granule>"}]
      (is (= expected (subscriptions/create-notification-message-body concept)) "JSON test")
      (is (= expected (subscriptions/create-notification-message-body xml-concept)) "XML test"))))

(deftest create-message-attributes-test
  (testing "Creating the message attributes."
    (let [collection-concept-id "C12345-PROV1"
          mode "New"
          subscriber "user1"]
      (is {"collection-concept-id" "C12345-PROV1"
           "mode" "New"
           "subsciber" "user1"}
          (subscriptions/create-message-attributes collection-concept-id mode subscriber)))))

(deftest create-message-subject-test
  (testing "Creating the message subject."
    (let [mode "Delete"]
      (is (= "Delete Notification"
             (subscriptions/create-message-subject mode))))))

(defn set-db-result
  "Sets the mock db result with a real queue endpoint to
  test real subscriptions and publishing of a message."
  [queue-url]
  (conj '()
        {:revision-id 1
         :deleted false
         :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
         :provider-id "PROV1"
         :user-id "ECHO_SYS"
         :transaction-id "2000000009M"
         :native-id "erichs_ingest_subscription"
         :concept-id "SUB1200000005-PROV1"
         :metadata (format
                    "{\"SubscriberId\":\"user1\",
                      \"CollectionConceptId\":\"C1200000002-PROV1\",
                      \"EndPoint\":\"%s\",
                      \"Mode\":[\"New\",\"Delete\"],
                      \"Method\":\"ingest\",
                      \"EmailAddress\":\"erich.e.reiter@nasa.gov\",
                      \"Query\":\"collection-concept-id=C1200000002-PROV1\",
                      \"Name\":\"Ingest-Subscription-Test\",
                      \"Type\":\"granule\",
                      \"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1\",\"Name\":\"UMM-Sub\",\"Version\":\"1.1.1\"}}"
                    queue-url)
         :revision-date "2024-10-07T18:13:32.608Z"
         :extra-fields {:normalized-query "76c6d7a828ef81efb3720638f335f65c"
                        :subscription-type "granule"
                        :subscription-name "Ingest-Subscription-Test"
                        :subscriber-id "user1"
                        :collection-concept-id "C1200000002-PROV1"}
         :concept-type :subscription}))

(defn get-cmr-internal-subscription-queue-url
  "helper function for the work-potential-notitification-test
  to get the internal subscription queue url to receive messages
  from it."
  [test-context]
  (let [topic (get-in test-context [:system :sns :internal])
        internal-subscriptions @(:subscription-atom topic)
        internal-sub (first (filter #(nil? (:concept-id %)) internal-subscriptions))]
    (:queue-url internal-sub)))

(defn get-cmr-subscription-dead-letter-queue-url
  "helper function for the work-potential-notitification-test
  to get the internal subscription queue url to receive messages
  from it."
  [test-context sub-concept-id]
  (let [topic (get-in test-context [:system :sns :internal])
        internal-subscriptions @(:subscription-atom topic)
        internal-sub (first (filter #(= sub-concept-id (:concept-id %)) internal-subscriptions))]
    (:dead-letter-queue-url internal-sub)))

(defn check-messages-and-contents
  "This function checks to see if a message was received from a queue
  and if it was, it checks the contents for the work-potiential-notification-test.
  If a message was not received a test failure is produced."
  [messages sqs-client queue-url]
  (let [message (first messages)]
    ;; check to see if a message exists
    (is (some? message))
    ;; don't check the message contents if the message doesn't exist because an exception is thrown.
    (when (some? message)
      (let [message-str (.body message)
            message (json/decode message-str true)]
        (is (= "G12345-PROV1" (:concept-id message)))
        (is (= '(:concept-id) (keys message)) "expected output for external subscription_worker")
        (is (some? (queue/delete-messages sqs-client queue-url messages)))))))

(deftest publish-subscription-notification-test
  (let [cache-key subscription-cache/subscription-cache-key
        test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}
                               :sns {:internal (pub-sub/create-topic nil)
                                     :external (pub-sub/create-topic nil)}}}
        sqs-client (queue/create-sqs-client (msg-config/sqs-server-url))
        queue-name "cmr-subscription-client-test-queue"
        queue-url  (queue/create-queue sqs-client queue-name)
        db-result (set-db-result queue-url)
        concept-metadata (format "{\"CollectionConceptId\": \"C1200000002-PROV1\",
                                   \"EndPoint\": \"%s\",
                                   \"Mode\":[\"New\", \"Delete\"],
                                   \"Method\":\"ingest\",
                                   \"SubscriberId\":\"user1\"}"
                                 queue-url)]

    (testing "Concept not a granule"
      (is (nil? (subscriptions/publish-subscription-notification-if-applicable test-context {:concept-type :collection}))))
    (testing "Concept is a granule, but not in ingest subscription cache."
      (is (nil? (subscriptions/publish-subscription-notification-if-applicable test-context {:concept-type :granule
                                                                                             :extra-fields {:parent-collection-id "C12349-PROV1"}}))))
    (testing "Concept will get published."
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result)}
        (let [sub-concept {:metadata concept-metadata
                           :concept-type :subscription
                           :concept-id "SUB1200000005-PROV1"}
              granule-concept {:concept-type :granule
                               :deleted false
                               :revision-id 1
                               :concept-id "G12345-PROV1"
                               :metadata "{\"GranuleUR\": \"GranuleUR\",
                                           \"DataGranule\": {\"Identifiers\": [{\"IdentifierType\": \"ProducerGranuleId\",
                                                                                \"Identifier\": \"Algorithm-1\"}]}}"
                               :extra-fields {:parent-collection-id "C1200000002-PROV1"}}]

          ;; if successful, the subscription concept-id is returned for local topic.
          (subscriptions/add-or-delete-ingest-subscription-in-cache test-context sub-concept)
          (is (= (:concept-id sub-concept) (get-in (subscriptions/attach-subscription-to-topic test-context sub-concept) [:extra-fields :aws-arn])))

          ;; the subscription is replaced when the subscription already exists.
          (subscriptions/add-or-delete-ingest-subscription-in-cache test-context sub-concept)
          (is (= (:concept-id sub-concept) (get-in (subscriptions/attach-subscription-to-topic test-context sub-concept) [:extra-fields :aws-arn])))

          ;; For this test add the subscription to the internal topic to test publishing.
          (let [topic (get-in test-context [:system :sns :internal])
                sub-concept-edn (subscriptions/add-or-delete-ingest-subscription-in-cache test-context sub-concept)]
            (topic-protocol/subscribe topic sub-concept-edn))

          ;; publish message. this should publish to 2 queues, the normal internal queue and to the client-test-queue.
          (is (some? (subscriptions/publish-subscription-notification-if-applicable test-context granule-concept)))

          ;; Get message from subscribed queue.
          (check-messages-and-contents (queue/receive-messages sqs-client queue-url) sqs-client queue-url)

          ;; Get message from infrastructure internal queue.
          (let [internal-queue-url (get-cmr-internal-subscription-queue-url test-context)]
            (check-messages-and-contents (queue/receive-messages sqs-client internal-queue-url) sqs-client internal-queue-url))

          ;; Test sending to dead letter queue.
          (is (some? (queue/delete-queue sqs-client queue-url)))
          (subscriptions/publish-subscription-notification-if-applicable test-context granule-concept)

          ;; Receive message from dead letter queue.
          (let [dead-letter-queue-url (get-cmr-subscription-dead-letter-queue-url test-context (sub-concept :concept-id))]
            (check-messages-and-contents (queue/receive-messages sqs-client dead-letter-queue-url) sqs-client dead-letter-queue-url))

          ;; Just delete the message from the internal infrastructure queue.
          (let [internal-queue-url (get-cmr-internal-subscription-queue-url test-context)
                messages (queue/receive-messages sqs-client internal-queue-url)]
            (is (some? messages))
            (when messages
              (is (some? (queue/delete-messages sqs-client internal-queue-url messages))))))))

    (testing "Concept will be unsubscribed."
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] (conj '() (-> (first (set-db-result queue-url))
                                                                                                              (assoc :deleted true))))}
        (let [sub-concept {:metadata concept-metadata
                           :deleted true
                           :concept-type :subscription
                           :concept-id "SUB1200000005-PROV1"}]
          (is (= (:concept-id sub-concept) (subscriptions/delete-ingest-subscription test-context sub-concept)))
          ;; Also remove subscription from internal queue.
          (let [topic (get-in test-context [:system :sns :internal])]
            (topic-protocol/unsubscribe topic sub-concept)))))))

 (defn work-potential-notification-with-real-aws
   "This function exists to manually test out the same code as
   tested above, but using real AWS instead of a mocked topic. This function is
   not ment to be run in bamboo. Redis needs to be up and running as does elasticmq."
   []
   (with-redefs [msg-config/queue-type (fn [] "aws")
                 msg-config/app-environment (fn [] "sit")]
     (println "internal topic: " (msg-config/cmr-internal-subscriptions-topic-name))
     (let [cache-key subscription-cache/subscription-cache-key
           test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}
                                  ;; These topic names are hard coded because the redefs are not working
                                  ;; when calling another namespace.
                                  :sns {:internal (pub-sub/create-topic "cmr-internal-subscriptions-sit")
                                        :external (pub-sub/create-topic "cmr-subscriptions-sit")}}}
           sqs-client (queue/create-sqs-client)
           queue-name "cmr-subscription-client-test-queue"
           queue-url  (queue/create-queue sqs-client queue-name)
           queue-arn (queue/get-queue-arn sqs-client queue-url)
           _ (println "queue-url:" queue-arn)
           db-result (set-db-result queue-arn)
           concept-metadata (format "{\"CollectionConceptId\": \"C1200000002-PROV1\",
                                      \"EndPoint\": \"%s\",
                                      \"Mode\":[\"New\", \"Delete\"],
                                      \"Method\":\"ingest\",
                                      \"SubscriberId\":\"user1\"}"
                                    queue-arn)]
       (testing "Concept will get published."
         (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result)}
           (let [sub-concept {:metadata concept-metadata
                              :concept-type :subscription
                              :concept-id "SUB1200000005-PROV1"}
                 granule-concept {:concept-type :granule
                                  :deleted false
                                  :revision-id 1
                                  :concept-id "G12345-PROV1"
                                  :metadata "{\"GranuleUR\": \"GranuleUR\",
                                                  \"DataGranule\": {\"Identifiers\": [{\"IdentifierType\": \"ProducerGranuleId\",
                                                                                       \"Identifier\": \"Algorithm-1\"}]}}"
                                  :extra-fields {:parent-collection-id "C1200000002-PROV1"}}
                 _ (subscriptions/add-or-delete-ingest-subscription-in-cache test-context sub-concept)
                 sub-concept (subscriptions/attach-subscription-to-topic test-context sub-concept)
                 subscription-arn (get-in sub-concept [:extra-fields :aws-arn])]

             (is (some? subscription-arn))

             ;; publish message. this should publish to the internal queue
             (is (some? (subscriptions/publish-subscription-notification-if-applicable test-context granule-concept)))

             (when subscription-arn
               (is (some? (subscriptions/delete-ingest-subscription test-context sub-concept))))

             (let [internal-queue-url "https://sqs.us-east-1.amazonaws.com/832706493240/cmr-internal-subscriptions-test-queue"
                   messages (queue/receive-messages sqs-client internal-queue-url)
                   message-str (.body (first messages))
                   message (json/decode message-str true)
                   real-message (json/decode (:Message message) true)]
               (println "message:" message)
               (println ":Message of message" (:Message message))
               (is (= "G12345-PROV1" (:concept-id real-message)))
               (is (= '(:concept-id :granule-ur :producer-granule-id :location) (keys real-message)))
               (is (some? (queue/delete-messages sqs-client internal-queue-url messages))))))))))

(deftest is-valid-sqs-arn-test
  (testing "sqs endpoint validation for subscription endpoint"
    (are3 [endpoint expected]
          (let [fun #'cmr.metadata-db.services.subscriptions/is-valid-sqs-arn]
            (is (= expected (fun endpoint))))

          "valid sqs endpoint"
          "arn:aws:sqs:us-west-1:123456789:Test-Queue"
          true

          "valid sqs endpoint with any string after sqs: "
          "arn:aws:sqs:anything after this is valid"
          true

          "invalid sqs endpoint"
          "some string"
          false

          "invalid sqs endpoint - because it's sns"
          "arn:aws:sns:blah blah"
          false

          "given nil"
          nil
          false
          )))

(deftest is-valid-subscription-endpoint-url-test
  (testing "url string validation for subscription endpoint"
    (are3 [endpoint expected]
          (let [fun #'cmr.metadata-db.services.subscriptions/is-valid-subscription-endpoint-url]
            (is (= expected (fun endpoint))))

          "valid local url -- with http prefix"
          "http://localhost:9324/000000000000"
          true

          "valid local url -- with https prefix"
          "https://localhost:9324/000000000000"
          true

          "valid url -- with https prefix"
          "https://www.google.com"
          true

          "invalid url - no https prefix"
          "www.google.com"
          false

          "invalid url -- no www prefix"
          "google.com"
          false

          "valid url -- with http prefix"
          "http://www.google.com"
          true

          "invalid url - non-existent domain"
          "hello.blach"
          false

          "invalid url - some string"
          "this is just some string"
          false

          "given nil"
          nil
          false
          )))

(deftest is-local-test-queue-test
  (testing "is local test queue endpoint for subscription endpoint validation"
    (are3 [endpoint expected]
          (let [fun #'cmr.metadata-db.services.subscriptions/is-local-test-queue]
            (is (= expected (fun endpoint))))

          "given remote sns queue endpoint"
          "http://localhost:9324"
          true

          "given longer remote sns queue endpoint"
          "http://localhost:9324/000000000/test-queue"
          true

          "given random string"
          "this is a random string"
          false

          "given empty string"
          ""
          false

          "given nil"
          nil
          false

          "given random url"
          "http://www.random.com"
          false
          )))

(deftest create-mode-to-endpoints-map
  (testing "creating mode to endpoints map test"
    (are3 [subscriptions expected]
          (is (= expected (subscriptions/create-mode-to-endpoints-map subscriptions)))

          "given nil subscriptions"
          nil
          {}

          "given empty subscriptions"
          []
          {}

          "given multiple subscriptions with two different endpoints and multiple modes each"
          [{:revision-id 1,
            :deleted false,
            :subscription-type "granule",
            :metadata {
                       :CollectionConceptId "C1111-JM_PROV1",
                       :EndPoint "sqs:arn:1",
                       :Mode ["New", "Update"],
                       :Method "ingest",
                       :Type "granule",
                       :SubscriberId "user-1"
                       },
            :extra-fields {:aws-arn "sqs:arn:1"},
            :concept-type :subscription},
           {:revision-id 1,
            :deleted false,
            :subscription-type "granule",
            :metadata {
                       :CollectionConceptId "C1111-JM_PROV1",
                       :EndPoint "https://www.url1.com",
                       :Mode ["New", "Delete"],
                       :Method "ingest",
                       :Type "granule",
                       :SubscriberId "user-2"
                       },
            :extra-fields {:aws-arn "https://www.url1.com"},
            :concept-type :subscription},
           {:revision-id 1,
            :deleted false,
            :subscription-type "granule",
            :metadata {
                       :CollectionConceptId "C1111-JM_PROV1",
                       :EndPoint "https://www.url2.com",
                       :Mode ["New"],
                       :Method "ingest",
                       :Type "granule",
                       :SubscriberId "user-2"
                       },
            :extra-fields {:aws-arn "https://www.url2.com"},
            :concept-type :subscription},
           {:revision-id 1,
            :deleted false,
            :subscription-type "granule",
            :metadata {
                       :CollectionConceptId "C1111-JM_PROV1",
                       :EndPoint "https://www.url2.com",
                       :Mode ["New"],
                       :Method "ingest",
                       :Type "granule",
                       :SubscriberId "user-3"
                       },
            :extra-fields {:aws-arn "https://www.url2.com"},
            :concept-type :subscription}]
          {"New" #{["sqs:arn:1" "user-1"], ["https://www.url1.com" "user-2"], ["https://www.url2.com" "user-2"], ["https://www.url2.com" "user-3"]}
           "Update" #{["sqs:arn:1" "user-1"]}
           "Delete" #{["https://www.url1.com" "user-2"]}}
          )))

(deftest attach-subscription-to-topic
    (let [concept-metadata "{\"CollectionConceptId\": \"C1200000002-PROV1\",
                             \"EndPoint\": \"some-endpoint\",
                             \"Mode\":[\"New\", \"Delete\"],
                             \"Method\":\"ingest\",
                             \"SubscriberId\":\"user1\"}"
          ingest-concept {:metadata concept-metadata
                          :concept-type :subscription
                          :concept-id "SUB1200000005-PROV1"}
          context {:system {:sns {:external "external-sns-topic"}}}
          non-ingest-concept {:metadata "{\"CollectionConceptId\": \"C1200000002-PROV1\",
                                          \"Mode\":[\"New\", \"Delete\"],
                                          \"Method\":\"search\"}"
                              :concept-type :subscription
                              :concept-id "SUB1200000005-PROV1"}]
      (with-redefs [topic-protocol/subscribe (fn [topic concept-edn] "sns-subscription-arn")]
        (testing "concept given is not ingest, will return input concept"
          (is (= non-ingest-concept (subscriptions/attach-subscription-to-topic context non-ingest-concept))))
        (testing "subscription succeeds, returns concept with aws-arn attached"
          (is (= (assoc-in ingest-concept [:extra-fields :aws-arn] "sns-subscription-arn") (subscriptions/attach-subscription-to-topic context ingest-concept))))
        )
      (with-redefs [topic-protocol/subscribe (fn [topic concept-edn] (throw (Exception. "exception from AWS")))]
        (testing "subscribe fails, will return concept without the aws-arn in extra fields and will NOT throw exception"
          (is (thrown? Exception (subscriptions/attach-subscription-to-topic context ingest-concept)))))
      (with-redefs [topic-protocol/subscribe (fn [topic concept-edn] nil)]
        (testing "subscribe fails, will return concept without the aws-arn in extra fields and will NOT throw exception"
          (is (= ingest-concept (subscriptions/attach-subscription-to-topic context ingest-concept)))))))
