(ns cmr.metadata-db.test.services.subscriptions-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]
   [cmr.metadata-db.services.subscriptions :as subscriptions] 
   [cmr.redis-utils.test.test-util :as test-util]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

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
        (is (= value subscriptions/subscriptions-enabled?))))
    (testing "Testing if a passed in concept is a subscription concept"
      (is (subscriptions/subscription-concept? :subscription {:extra-fields {:endpoint "some-endpoint" :method "ingest"}})))
    (testing "Testing if a passed in concept is a granule concept"
      (is (subscriptions/granule-concept? :granule)))
    (testing "Add a subscription"
      (is (= 1 (subscriptions/add-subscription test-context :subscription {:extra-fields {:collection-concept-id "C12345-PROV1"
                                                                                          :mode ["New"]
                                                                                          :endpoint "some-endpoint"
                                                                                          :method "ingest"}}))))
    (testing "Delete a subscription"
      (is (= 1 (subscriptions/delete-subscription test-context :subscription {:extra-fields {:collection-concept-id "C12345-PROV1"
                                                                                             :mode ["New"]
                                                                                             :endpoint "some-endpoint"
                                                                                             :method "ingest"}}))))
    (testing "adding and removing subscriptions."
      (are3
       [delete expected example-record]
       (if delete
         (do
           (subscriptions/delete-subscription test-context :subscription example-record)
           (is (= expected (hash-cache/get-map cache-client cache-key))))
         (do
           (subscriptions/add-subscription test-context :subscription example-record)
           (is (= expected (hash-cache/get-map cache-client cache-key)))))

       "Adding 1 update subscription"
       false
       {"C12345-PROV1" {"Update" 1}}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       "Adding 2 update subscription"
       false
       {"C12345-PROV1" {"Update" 2}}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       "Adding 2 update subscription"
       false
       {"C12345-PROV1" {"New" 1
                        "Update" 2
                        "Delete" 1}}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["New" "Delete"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       "Removing 1 subscription"
       true
       {"C12345-PROV1" {"New" 1
                        "Update" 2}}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Delete"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       "Removing 2 subscription"
       true
       {"C12345-PROV1" {"Update" 1}}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["New" "Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       "Removing last subscription"
       true
       nil
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       "Try to remove something that doesn't exist"
       true
       nil
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}))))

(def db-result
  '({:revision-id 1
     :deleted "false"
     :format "application/vnd.nasa.cmr.umm+json;version=1.1"
     :provider-id "CMR"
     :user-id "ECHO_SYS"
     :transaction-id "2000000003M"
     :native-id "erichs_subscription"
     :concept-id SUB1200000001-CMR
     :metadata "{\"Name\":\"someSubscription\",
                \"Type\":\"collection\",
                \"SubscriberId\":\"eereiter\",
                \"EmailAddress\":\"erich.e.reiter@nasa.gov\",
                \"Query\":\"bounding_box=-180,-90,180,90\",
                \"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/subscription/v1.1\",\"Name\":\"UMM-Sub\",\"Version\":\"1.1\"}}"
     :revision-date "2024-10-07T18:08:51.018Z"
     :extra-fields {:normalized-query "1e9a7eaf3ed5acfb1bddb2a184658506"
                    :subscription-type "collection"
                    :subscription-name "someSubscription"
                    :subscriber-id "eereiter"
                    :collection-concept-id nil}
     :concept-type :subscription}
    {:revision-id 1
     :deleted "false"
     :format "application/vnd.nasa.cmr.umm+json;version=1.1.1"
     :provider-id "PROV1"
     :user-id "ECHO_SYS"
     :transaction-id "2000000009M"
     :native-id "erichs_ingest_subscription"
     :concept-id "SUB1200000005-PROV1"
     :metadata "{\"SubscriberId\":\"eereiter\",
                \"CollectionConceptId\":\"C1200000002-PROV1\",
                \"EndPoint\":\"arn\",
                \"Mode\":[\"New\",\"Update\",\"Delete\"],
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

(deftest subscription-refresh-cache-test
  (let [cache-key subscription-cache/subscription-cache-key
        test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}}}
        cache-client (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache-client cache-key)
    (subscriptions/add-subscription test-context :subscription {:extra-fields {:collection-concept-id "C1200000002-PROV1"
                                                                               :mode ["New" "Update"]
                                                                               :endpoint "some-endpoint"
                                                                               :method "ingest"}})
    (subscriptions/add-subscription test-context :subscription {:extra-fields {:collection-concept-id "C12346-PROV1"
                                                                               :mode ["New" "Update"]
                                                                               :endpoint "some-endpoint"
                                                                               :method "ingest"}})
    (testing "What is in the cache"
      (is (= {"C1200000002-PROV1" {"New" 1 "Update" 1}
              "C12346-PROV1" {"New" 1 "Update" 1}}
             (hash-cache/get-map cache-client cache-key))))
    (testing "Cache needs to be updated."
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context] db-result)}
        (subscriptions/refresh-subscription-cache test-context))
      (is (= {"C1200000002-PROV1" {"New" 1 "Update" 1 "Delete" 1}}
             (hash-cache/get-map cache-client cache-key))))
    (testing "Testing no subscriptions"
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context] '())}
        (subscriptions/refresh-subscription-cache test-context))
      (is (nil? (hash-cache/get-map cache-client cache-key))))))
