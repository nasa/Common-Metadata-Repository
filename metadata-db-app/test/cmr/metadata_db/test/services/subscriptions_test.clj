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
                                                                     \"Method\":\"ingest\"}",
                                                         :extra-fields {:collection-concept-id "C12345-PROV1"}}))}
        (is (= 1 (subscriptions/change-subscription-in-cache test-context {:concept-type :subscription
                                                                  :deleted               false
                                                                  :metadata              {:CollectionConceptId "C12345-PROV1"
                                                                             :EndPoint "some-endpoint"
                                                                             :Mode ["New"]
                                                                             :Method "ingest"}
                                                                  :extra-fields          {:collection-concept-id "C12345-PROV1"}})))))
    (testing "Delete a subscription from the cache"
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] '({:concept-type :subscription
                                                                                                   :deleted true
                                                                                                   :metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                                                                                                               \"EndPoint\":\"some-endpoint\",
                                                                                                               \"Mode\":[\"New\"],
                                                                                                               \"Method\":\"ingest\"}",
                                                                                                   :extra-fields {:collection-concept-id "C12345-PROV1"}}))}
        (is (= 1 (subscriptions/change-subscription-in-cache test-context {:concept-type :subscription
                                                                   :deleted              true
                                                                   :metadata             {:CollectionConceptId "C12345-PROV1"
                                                                              :EndPoint "some-endpoint"
                                                                              :Mode ["New"]
                                                                              :Method "ingest"}
                                                                   :extra-fields         {:collection-concept-id "C12345-PROV1"}})))))
    (testing "Add-to-existing-mode"
      (are3
       [expected existing-modes new-modes]
       (is (= (set expected) 
              (set (subscriptions/add-to-existing-mode existing-modes new-modes))))

       "new subscription with no existing modes in cache."
       ["New" "Update" "Delete"]
       nil
       ["New" "Update" "Delete"]

       "new subscription with empty existing modes in cache."
       ["Delete"]
       []
       ["Delete"]

       "Adding new subscription with existing mode in cache of a different value."
       ["Delete" "New"]
       ["New"]
       ["Delete"]

       "Adding new subscription with existing modes."
       ["New" "Delete"]
       ["New"]
       ["New" "Delete"]

       "Adding duplicate subscription with existing modes."
       ["New" "Delete"]
       ["New" "Delete"]
       ["New" "Delete"]))

    (testing "Merge modes"
      (are3
       [expected subscriptions]
       (is (= (set expected) (set (subscriptions/merge-modes subscriptions))))

       "merge 1 mode."
       ["Update"]
       '({:metadata {:Mode ["Update"]}})

       "Merge several modes"
       ["New" "Update" "Delete"]
       '({:metadata {:Mode ["Update"]}}
         {:metadata {:Mode ["New"]}}
         {:metadata {:Mode ["Delete"]}})

       "Merge several modes 2"
       ["New" "Update" "Delete"]
       '({:metadata {:Mode ["Update"]}}
         {:metadata {:Mode ["New" "Delete"]}})

       "Merge several modes 3 with duplicates."
       ["New" "Update" "Delete"]
       '({:metadata {:Mode ["Update"]}}
         {:metadata {:Mode ["New" "Delete"]}}
         {:metadata {:Mode ["New"]}}
         {:metadata {:Mode ["New" "Update"]}})))

    (testing "adding and removing subscriptions from the cache."
      (are3
       [expected example-record db-contents]
       (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-contents)}
         (subscriptions/change-subscription-in-cache test-context example-record)
         (is (= expected (create-value-set (hash-cache/get-map cache-client cache-key)))))

       "Adding 1 subscription"
       {"C12345-PROV1" (set ["Update"])}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"Update\"],
                      \"Method\":\"ingest\"}",
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Adding duplicate subscription"
       {"C12345-PROV1" (set ["Update"])}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"Update\"],
                      \"Method\":\"ingest\"}",
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Adding override subscription"
       {"C12345-PROV1" (set ["New" "Delete"])}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\"}"
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Adding new subscription that matches the one from before."
       {"C12345-PROV1" (set ["New" "Delete"])
        "C12346-PROV1" (set ["New" "Delete"])}
       {:metadata {:CollectionConceptId "C12346-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12346-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\"}",
          :extra-fields {:collection-concept-id "C12346-PROV1"}
          :deleted false
          :concept-type :subscription})

       "Removing 1 subscription"
       {"C12346-PROV1" (set ["New" "Delete"])}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       ;; even though C12346-PROV1 is in the db, we are search only for
       ;; concepts with the collection-concept-id.
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                      \"EndPoint\":\"some-endpoint\",
                      \"Mode\":[\"New\", \"Delete\"],
                      \"Method\":\"ingest\"}"
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted true
          :concept-type :subscription})

       "Removing same subscription"
       {"C12346-PROV1" (set ["New" "Delete"])}
       {:metadata {:CollectionConceptId "C12345-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                               \"EndPoint\":\"some-endpoint\",
                               \"Mode\":[\"New\", \"Delete\"],
                               \"Method\":\"ingest\"}"
          :extra-fields {:collection-concept-id "C12345-PROV1"}
          :deleted true
          :concept-type :subscription})

       "Removing last subscription"
       nil
       {:metadata {:CollectionConceptId "C12346-PROV1"}}
       '({:metadata "{\"CollectionConceptId\":\"C12346-PROV1\",
                                        \"EndPoint\":\"some-endpoint\",
                                        \"Mode\":[\"New\", \"Delete\"],
                                        \"Method\":\"ingest\"}",
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
                             :Method "ingest"}
                  :concept-type :subscription}
                 (subscriptions/add-or-delete-ingest-subscription-in-cache test-context {:metadata "{\"CollectionConceptId\":\"C12345-PROV1\",
                                                                                  \"EndPoint\":\"ARN\",
                                                                                  \"Mode\":[\"New\", \"Delete\"],
                                                                                  \"Method\":\"ingest\"}"
                                                                      :concept-type                :subscription}))))))))

(def db-result-1
  '({:revision-id 1
     :deleted false
     :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
     :provider-id "PROV1"
     :user-id "ECHO_SYS"
     :transaction-id "2000000009M"
     :native-id "erichs_ingest_subscription"
     :concept-id "SUB1200000005-PROV1"
     :metadata "{\"SubscriberId\":\"eereiter\",
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
                    :subscriber-id "eereiter"
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
     :metadata "{\"SubscriberId\":\"eereiter\",
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
                    :subscriber-id "eereiter"
                    :collection-concept-id "C12346-PROV1"}
     :concept-type :subscription}))

(def db-result-3
 (concat db-result-1
         db-result-2
         '({:revision-id 1
            :deleted false
            :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
            :provider-id "PROV1"
            :user-id "ECHO_SYS"
            :transaction-id "2000000011M"
            :native-id "erichs_ingest_subscription3"
            :concept-id "SUB1200000008-PROV1"
            :metadata "{\"SubscriberId\":\"eereiter\",
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
                           :subscriber-id "eereiter"
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
     :metadata "{\"SubscriberId\":\"eereiter\",
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
                    :subscriber-id "eereiter"
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
      (is (= {"C1200000002-PROV1" (set ["New" "Delete"])
              "C12346-PROV1" (set ["New" "Update"])
              "C1200000003-PROV1" (set ["New" "Delete"])}
             (create-value-set (hash-cache/get-map cache-client cache-key)))))
    (testing "Cache needs to be updated."
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context] db-result-3)}
        (subscriptions/refresh-subscription-cache test-context))
      (is (= {"C1200000002-PROV1" (set ["New" "Update" "Delete"])
              "C12346-PROV1" (set ["New" "Update"])}
             (create-value-set (hash-cache/get-map cache-client cache-key)))))
    (testing "Testing no subscriptions"
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context] '())}
        (subscriptions/refresh-subscription-cache test-context))
      (is (nil? (hash-cache/get-map cache-client cache-key))))))

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

(deftest create-notification-test
  (testing "Getting the notification for a concept."
    (let [concept {:concept-id "G12345-PROV1"
                   :revision-id 1
                   :metadata "{\"GranuleUR\": \"GranuleUR\",
                               \"DataGranule\": {\"Identifiers\": [{\"IdentifierType\": \"ProducerGranuleId\",
                                                                    \"Identifier\": \"Algorithm-1\"}]}}"}]
      (is (= (str "{\"concept-id\": \"G12345-PROV1\", "
                  "\"granule-ur\": \"GranuleUR\", "
                  "\"producer-granule-id\": \"Algorithm-1\", "
                  "\"location\": \"http://localhost:3003/concepts/G12345-PROV1/1\"}")
             (subscriptions/create-notification concept))))))

(deftest create-message-attributes-test
  (testing "Creating the message attributes."
    (let [collection-concept-id "C12345-PROV1"
          mode "New"]
      (is {"collection-concept-id" "C12345-PROV1"
           "mode" "New"}
          (subscriptions/create-message-attributes collection-concept-id mode)))))

(deftest create-message-subject-test
  (testing "Creating the message subject."
    (let [mode "Delete"]
      (is (= "Delete Notification"
             (subscriptions/create-message-subject mode))))))

(deftest get-attributes-and-subject-test
  (testing "Getting notificaiton attributes and subject."
    (are3
     ;;concept mode coll-concept-id
     [expected concept mode coll-concept-id]
     (is (= expected
            (subscriptions/create-attributes-and-subject-map concept mode coll-concept-id)))

     "Deleted concept"
     {:attributes {"collection-concept-id" "C12345-PROV1"
                   "mode" "Delete"}
      :subject "Delete Notification"}
     {:deleted true}
     ["New" "Delete"]
     "C12345-PROV1"

     "Deleted concept, but not looking for the mode."
     nil
     {:deleted true}
     ["New" "Update"]
     "C12345-PROV1"

     "Deleted concept, but not looking for the mode, making sure no other condition is met."
     nil
     {:deleted true
      :revision-id 2}
     ["New" "Update"]
     "C12345-PROV1"

     "New concept."
     {:attributes {"collection-concept-id" "C12345-PROV1"
                   "mode" "New"}
      :subject "New Notification"}
     {:deleted false
      :revision-id 1}
     ["New" "Update"]
     "C12345-PROV1"

     "Update concept."
     {:attributes {"collection-concept-id" "C12345-PROV1"
                   "mode" "Update"}
      :subject "Update Notification"}
     {:deleted false
      :revision-id 3}
     ["New" "Update"]
     "C12345-PROV1")))

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
                    "{\"SubscriberId\":\"eereiter\",
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
                        :subscriber-id "eereiter"
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
        (is (= '(:concept-id :granule-ur :producer-granule-id :location) (keys message)))
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
                                   \"Method\":\"ingest\"}"
                                 queue-url)]

    (testing "Concept not a granule"
      (is (nil? (subscriptions/publish-subscription-notification-if-applicable test-context {:concept-type :collection}))))
    (testing "Concept is a granule, but not in ingest subscription cache."
      (is (nil? (subscriptions/publish-subscription-notification-if-applicable test-context {:concept-type :granule
                                                                         :extra-fields                     {:parent-collection-id "C12349-PROV1"}}))))
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

          ;; publish message. this should publish to 2 queues, the normal internal queue and to
          ;; the client-test-queue.
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

          ;; Just delete the message from the internal infrastrcture queue.
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
                                      \"Method\":\"ingest\"}"
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
                 subscription-arn (subscriptions/attach-subscription-to-topic test-context sub-concept)
                 sub-concept (assoc-in sub-concept [:extra-fields :aws-arn] subscription-arn)]

             (is (some? subscription-arn))
             (when subscription-arn
               (is (some? (subscriptions/delete-ingest-subscription test-context sub-concept))))

             ;; publish message. this should publish to the internal queue
             (is (some? (subscriptions/publish-subscription-notification-if-applicable test-context granule-concept)))

             (let [internal-queue-url "https://sqs.us-east-1.amazonaws.com/832706493240/cmr-internal-subscriptions-queue-sit"
                   messages (queue/receive-messages sqs-client internal-queue-url)
                   message-str (.body (first messages))
                   message (json/decode message-str true)
                   real-message (json/decode (:Message message) true)]
               (println "message:" message)
               (println ":Message of message" (:Message message))
               (is (= "G12345-PROV1" (:concept-id real-message)))
               (is (= '(:concept-id :granule-ur :producer-granule-id :location) (keys real-message)))
               (is (some? (queue/delete-messages sqs-client internal-queue-url messages))))))))))

(deftest set-subscription-arn-if-applicable-test
  (testing "set-subscription-arn returns un-changed concept"
    (are3 [concept-type concept expected]
          (is (= expected (subscriptions/set-subscription-arn-if-applicable nil concept-type concept)))

          "non-subscription concept type returns un-changed concept"
          :granule
          {:metadata {:EndPoint ""}}
          {:metadata {:EndPoint ""}}

          "empty endpoint returns un-changed concept"
          :subscription
          {:metadata (json/encode {:EndPoint ""})}
          {:metadata (json/encode {:EndPoint ""})}))
  (with-redefs [cmr.metadata-db.services.subscriptions/attach-subscription-to-topic (fn [context concept] {:metadata (json/encode {"EndPoint" "http://localhost:9324/000000000/"}) :extra-fields {:aws-arn "SUB1234"}})]
    (testing "local test queue url endpoint returns changed concept"
      (let [concept {:metadata (json/encode {"EndPoint" "http://localhost:9324/000000000/"})}
            expected-concept {:metadata (json/encode {"EndPoint" "http://localhost:9324/000000000/"}) :extra-fields {:aws-arn "SUB1234"}}]
        (is (= expected-concept (subscriptions/set-subscription-arn-if-applicable nil :subscription concept)))))

    (testing "http endpoint returns changed concept"
      (let [concept {:metadata (json/encode {"EndPoint" "http://www.endpoint.com"})}
            expected-concept {:metadata (json/encode {"EndPoint" "http://www.endpoint.com"}) :extra-fields {:aws-arn "http://www.endpoint.com"}}]
        (is (= expected-concept (subscriptions/set-subscription-arn-if-applicable nil :subscription concept))))))

  (with-redefs [cmr.metadata-db.services.subscriptions/attach-subscription-to-topic (fn [context concept] {:metadata (json/encode {"EndPoint" "arn:aws:sqs:1234:Queue-Name"}) :extra-fields {:aws-arn "sqs:arn"}})]
    (testing "sqs arn endpoint returns changed concept"
      (let [concept {:metadata (json/encode {:EndPoint "arn:aws:sqs:1234:Queue-Name"})}
            expected-concept {:metadata (json/encode {"EndPoint" "arn:aws:sqs:1234:Queue-Name"}) :extra-fields {:aws-arn "sqs:arn"}}]
        (is (= expected-concept (subscriptions/set-subscription-arn-if-applicable nil :subscription concept)))))))

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

(deftest attach-subscription-to-topic-test
    (with-redefs [cmr.message-queue.topic.topic-protocol/subscribe (fn [topic concept] "subscription-arn")]
      (testing "concept is not ingest subscription - returns un-changed concept"
        (let [concept {:metadata (json/encode {:Method "search"})}]
          (is (= concept (subscriptions/attach-subscription-to-topic {:system {:sns {:external "default-topic"}}} concept)))))
      (testing "subscribing to topic succeeded -- returns updated concept with new aws-arn field"
        (let [concept {:metadata (json/encode {:Method "ingest" :EndPoint "arn:aws:sqs:1234:Queue-Name"})}
              expected-concept {:metadata (json/encode {:Method "ingest" :EndPoint "arn:aws:sqs:1234:Queue-Name"})
                                :extra-fields {:aws-arn "subscription-arn"}}]
          (is (= expected-concept (subscriptions/attach-subscription-to-topic {:system {:sns {:external "default-topic"}}} concept))))))
    (with-redefs [cmr.message-queue.topic.topic-protocol/subscribe (fn [topic concept] nil)]
      (testing "subscribing to topic returned nil - returns un-changed concept"
        (let [concept {:metadata (json/encode {:Method "ingest" :EndPoint "arn:aws:sqs:1234:Queue-Name"})}]
          (is (= concept (subscriptions/attach-subscription-to-topic {:system {:sns {:external "default-topic"}}} concept))))))
    (with-redefs [cmr.message-queue.topic.topic-protocol/subscribe (fn [topic concept] (throw (Exception. "some exception")))]
      (testing "subscribing to topic failed and threw error - returns un-changed concept"
        (let [concept {:metadata (json/encode {:Method "ingest" :EndPoint "arn:aws:sqs:1234:Queue-Name"})}]
          (is (= concept (subscriptions/attach-subscription-to-topic {:system {:sns {:external "default-topic"}}} concept)))))))

;;TODO fix this test
(deftest delete-ingest-subscription-test
  (let [context {:system {:sns {:external "default-topic"}}}
        expected-subscription-arn "subscription-arn"]
    (with-redefs [cmr.message-queue.topic.topic-protocol/unsubscribe (fn [topic concept] expected-subscription-arn)]
      (testing "is valid sqs arn"
        (let [concept {:metadata (json/encode {:Method "ingest" :EndPoint "arn:aws:sqs:1234:Queue-Name"})
                       :extra-fields {:aws-arn "subscription-arn"}}
              concept-edn {:metadata {:Method "ingest" :EndPoint "arn:aws:sqs:1234:Queue-Name"}
                           :extra-fields {:aws-arn "subscription-arn"}}]
          (with-redefs [cmr.metadata-db.services.subscriptions/add-or-delete-ingest-subscription-in-cache (fn [context concept] concept-edn)]
            (is (= expected-subscription-arn (subscriptions/delete-ingest-subscription context concept)))
            )))
      (testing "is local test queue"
        (let [concept {:metadata (json/encode {:Method "ingest" :EndPoint "http://localhost:9324/000000000/test-queue"})
                       :extra-fields {:aws-arn "subscription-arn"}}
              concept-edn {:metadata {:Method "ingest" :EndPoint "http://localhost:9324/000000000/test-queue"}
                           :extra-fields {:aws-arn "subscription-arn"}}]
          (with-redefs [cmr.metadata-db.services.subscriptions/add-or-delete-ingest-subscription-in-cache (fn [context concept] concept-edn)]
            (is (= expected-subscription-arn (subscriptions/delete-ingest-subscription context concept))))))
      (testing "is valid http url"
        (let [concept {:metadata (json/encode {:Method "ingest" :EndPoint "http://www.endpoint.com"})
                       :extra-fields {:aws-arn "http://www.endpoint.com"}}
              concept-edn {:metadata {:Method "ingest" :EndPoint "http://www.endpoint.com"}
                           :extra-fields {:aws-arn "http://www.endpoint.com"}}]
          (with-redefs [cmr.metadata-db.services.subscriptions/add-or-delete-ingest-subscription-in-cache (fn [context concept] concept-edn)]
            (is (= "http://www.endpoint.com" (subscriptions/delete-ingest-subscription context concept))))))
      (testing "is invalid endpoint"
        (let [concept {:metadata (json/encode {:Method "ingest" :EndPoint "random"}) :extra-fields {:aws-arn "random"}}
              concept-edn {:metadata {:Method "ingest" :EndPoint "random"} :extra-fields {:aws-arn "random"}}]
          (with-redefs [cmr.metadata-db.services.subscriptions/add-or-delete-ingest-subscription-in-cache (fn [context concept] concept-edn)]
            (is (= "random" (subscriptions/delete-ingest-subscription context concept))))))
      (testing "non-ingest subscription concept given"
        (let [concept {:metadata (json/encode {:Method "search"})}
              concept-edn {:metadata {:Method "search"}}]
          (with-redefs [cmr.metadata-db.services.subscriptions/add-or-delete-ingest-subscription-in-cache (fn [context concept] concept-edn)]
            (is (= nil (subscriptions/delete-ingest-subscription context concept))))))
    )))
