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
        (is (= value subscriptions/subscriptions-enabled?))))
    (testing "Testing if a passed in concept is a subscription concept"
      (is (subscriptions/subscription-concept? :subscription {:extra-fields {:endpoint "some-endpoint" :method "ingest"}})))
    (testing "Testing if a passed in concept is a granule concept"
      (is (subscriptions/granule-concept? :granule)))
    (testing "Add a subscription"
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] '({:extra-fields {:subscription-type "granule"
                                                                                                                  :endpoint "some-endpoint"
                                                                                                                  :method "ingest"
                                                                                                                  :mode ["New"]
                                                                                                                  :collection-concept-id "C12345-PROV1"}
                                                                                                   :concept-type :subscription}))}
        (is (= 1 (subscriptions/change-subscription test-context :subscription {:extra-fields {:subscription-type "granule"
                                                                                               :endpoint "some-endpoint"
                                                                                               :method "ingest"
                                                                                               :mode ["New"]
                                                                                               :collection-concept-id "C12345-PROV1"}})))))
    (testing "Delete a subscription"
      (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] '())}
        (is (= 1 (subscriptions/change-subscription test-context :subscription {:extra-fields {:collection-concept-id "C12345-PROV1"
                                                                                               :mode ["New"]
                                                                                               :endpoint "some-endpoint"
                                                                                               :method "ingest"}})))))
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
       '({:extra-fields {:mode ["Update"]}})

       "Merge several modes"
       ["New" "Update" "Delete"]
       '({:extra-fields {:mode ["Update"]}}
         {:extra-fields {:mode ["New"]}}
         {:extra-fields {:mode ["Delete"]}})

       "Merge several modes 2"
       ["New" "Update" "Delete"]
       '({:extra-fields {:mode ["Update"]}}
         {:extra-fields {:mode ["New" "Delete"]}})

       "Merge several modes 3 with duplicates."
       ["New" "Update" "Delete"]
       '({:extra-fields {:mode ["Update"]}}
         {:extra-fields {:mode ["New" "Delete"]}}
         {:extra-fields {:mode ["New"]}}
         {:extra-fields {:mode ["New" "Update"]}})))

    (testing "adding and removing subscriptions."
      (are3
       [expected example-record db-contents]
       (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-contents)}
         (subscriptions/change-subscription test-context :subscription example-record)
         (is (= expected (create-value-set (hash-cache/get-map cache-client cache-key)))))

       "Adding 1 subscription"
       {"C12345-PROV1" (set ["Update"])}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       '({:extra-fields {:subscription-type "granule"
                         :endpoint "some-endpoint"
                         :method "ingest"
                         :mode ["Update"]
                         :collection-concept-id "C12345-PROV1"}
          :concept-type :subscription})

       "Adding duplicate subscription"
       {"C12345-PROV1" (set ["Update"])}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       '({:extra-fields {:subscription-type "granule"
                         :endpoint "some-endpoint"
                         :method "ingest"
                         :mode ["Update"]
                         :collection-concept-id "C12345-PROV1"}
          :concept-type :subscription})

       "Adding override subscription"
       {"C12345-PROV1" (set ["New" "Delete"])}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["New" "Delete"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       '({:extra-fields {:subscription-type "granule"
                         :endpoint "some-endpoint"
                         :method "ingest"
                         :mode ["New" "Delete"]
                         :collection-concept-id "C12345-PROV1"}
          :concept-type :subscription})

       "Adding new subscription that matches the one from before."
       {"C12345-PROV1" (set ["New" "Delete"])
        "C12346-PROV1" (set ["New" "Delete"])}
       {:extra-fields {:collection-concept-id "C12346-PROV1"
                       :mode ["New" "Delete"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       '({:extra-fields {:subscription-type "granule"
                         :endpoint "some-endpoint"
                         :method "ingest"
                         :mode ["New" "Delete"]
                         :collection-concept-id "C12345-PROV1"}
          :concept-type :subscription}
         {:extra-fields {:subscription-type "granule"
                         :collection-concept-id "C12346-PROV1"
                         :mode ["New" "Delete"]
                         :endpoint "some-endpoint"
                         :method "ingest"}
          :concept-type :subscription})

       "Removing 1 subscription"
       {"C12346-PROV1" (set ["New" "Delete"])}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["New" "Delete"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       ;; even though C12346-PROV1 is in the db, we are search only for
       ;; concepts with the collection-concept-id.
       '()

       "Removing same subscription"
       {"C12346-PROV1" (set ["New" "Delete"])}
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["New" "Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       '()

       "Removing last subscription"
       nil
       {:extra-fields {:collection-concept-id "C12346-PROV1"
                       :mode ["New" "Delete"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       '()

       "Try to remove something that doesn't exist"
       nil
       {:extra-fields {:collection-concept-id "C12345-PROV1"
                       :mode ["Update"]
                       :endpoint "some-endpoint"
                       :method "ingest"}}
       '()))))

(def db-result-1
  '({:revision-id 1
     :deleted "false"
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
                    :collection-concept-id "C1200000002-PROV1"
                    :endpoint "some-endpoint"
                    :mode ["New", "Delete"]
                    :method "ingest"}
     :concept-type :subscription}))

(def db-result-2
  '({:revision-id 1
     :deleted "false"
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
                    :endpoint "some-endpoint"
                    :mode ["New", "Update"]
                    :method "ingest"
                    :collection-concept-id "C12346-PROV1"}
     :concept-type :subscription}))

(def db-result-3
 (concat db-result-1
         db-result-2
         '({:revision-id 1
            :deleted "false"
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
     :deleted "false"
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
                    :collection-concept-id "C1200000003-PROV1"
                    :endpoint "some-endpoint"
                    :mode ["New", "Delete"]
                    :method "ingest"}
     :concept-type :subscription}))

(deftest subscription-refresh-cache-test
  (let [cache-key subscription-cache/subscription-cache-key
        test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}}}
        cache-client (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache-client cache-key)
    (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result-1)}
      (subscriptions/change-subscription test-context :subscription {:extra-fields {:collection-concept-id "C1200000002-PROV1"
                                                                                    :mode ["New" "Delete"]
                                                                                    :endpoint "some-endpoint"
                                                                                    :method "ingest"}}))
    (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result-2)}
      (subscriptions/change-subscription test-context :subscription {:extra-fields {:collection-concept-id "C12346-PROV1"
                                                                                    :mode ["New" "Update"]
                                                                                    :endpoint "some-endpoint"
                                                                                    :method "ingest"}}))
    (with-bindings {#'subscriptions/get-subscriptions-from-db (fn [_context _coll-concept-id] db-result-4)}
      (subscriptions/change-subscription test-context :subscription {:extra-fields {:collection-concept-id "C1200000003-PROV1"
                                                                                    :mode ["New", "Delete"]
                                                                                    :endpoint "some-endpoint"
                                                                                    :method "ingest"}}))
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
