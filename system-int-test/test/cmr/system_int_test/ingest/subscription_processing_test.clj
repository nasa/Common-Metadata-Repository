(ns cmr.system-int-test.ingest.subscription-processing-test
  "CMR subscription processing tests."
  (:require
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.ingest.services.jobs :as jobs]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.access-control.test.util :as ac-util]
   [cmr.common.time-keeper :as tk]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as data-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.subscription-util :as subscription-util]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.metadata-db :as mdb2]
   [ring.util.codec :as codec]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"})
    (subscription-util/grant-all-subscription-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2"}
     [:read :update]
     [:read :update])
    (subscription-util/grant-all-subscription-fixture {"provguid1" "PROV3"}
                                                      [:read]
                                                      [:read :update])
    (dev-system/freeze-resume-time-fixture)]))

(defn- result->query
  "extract just the Query out of a result from process-result->hash"
  [result]
  (let  [meta-raw (get-in result [:subscription :metadata])
         meta-json (json/parse-string meta-raw)]
    (get meta-json "Query")))

(defn- process-result->hash
  "Deconstruct a subscription process response from the function
       trigger-process-subscriptions into a hash table so that tests can references
       each value by name and not have to use (nth x #). The order is defined in
       email_processing.clj"
  [response]
  (map (fn [x]
         (let [[sub-id subscriber-filtered-gran-refs email-address subscription] x]
           {:sub-id sub-id
            :subscriber-filtered-gran-refs subscriber-filtered-gran-refs
            :email-address email-address
            :subscription subscription})) response))

(defn- trigger-process-subscriptions
  "Sets up process-subscriptions arguments. Calls process-subscriptions, returns granule concept-ids."
  []
  (let [subscriptions (->> (mdb2/find-concepts (system/context) {:latest true} :subscription)
                           (remove :deleted)
                           (mapv #(select-keys % [:concept-id :extra-fields :metadata])))]
    (#'jobs/process-subscriptions (system/context) subscriptions)))

(deftest ^:oracle subscription-email-processing-time-constraint-test
  (system/only-with-real-database
   (let [user2-group-id (echo-util/get-or-create-group (system/context) "group2")
         _user2-token (echo-util/login (system/context) "user2" [user2-group-id])
         _ (echo-util/ungrant (system/context)
                              (-> (access-control/search-for-acls (system/context)
                                                                  {:provider "PROV1"
                                                                   :identity-type "catalog_item"}
                                                                  {:token "mock-echo-system-token"})
                                  :items
                                  first
                                  :concept_id))

         _ (echo-util/grant (system/context)
                            [{:group_id user2-group-id
                              :permissions [:read]}]
                            :catalog_item_identity
                            {:provider_id "PROV1"
                             :name "Provider collection/granule ACL"
                             :collection_applicable true
                             :granule_applicable true
                             :granule_identifier {:access_value {:include_undefined_value true
                                                                 :min_value 1 :max_value 50}}})
         _ (ac-util/wait-until-indexed)
         ;; Setup collection
         coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                     (data-umm-c/collection {:ShortName "coll1"
                                                                             :EntryTitle "entry-title1"})
                                                     {:token "mock-echo-system-token"})

         _ (index/wait-until-indexed)
         ;; Setup subscriptions
         _ (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                   {:Name "test_sub_prov1"
                                                    :SubscriberId "user2"
                                                    :CollectionConceptId (:concept-id coll1)
                                                    :Query "provider=PROV1"})
                                                  {:token "mock-echo-system-token"})]

     (index/wait-until-indexed)

     (testing "First query executed does not have a last-notified-at and looks back 24 hours"
       (let [gran1 (data-core/ingest "PROV1"
                                     (data-granule/granule-with-umm-spec-collection coll1
                                                                                    (:concept-id coll1)
                                                                                    {:granule-ur "Granule1"
                                                                                     :access-value 1})
                                     {:token "mock-echo-system-token"})
             _ (index/wait-until-indexed)
             results (->> (trigger-process-subscriptions)
                          (map #(nth % 1))
                          flatten
                          (map :concept-id))]
         (is (= (:concept-id gran1)
                (first results)))))

     (dev-system/advance-time! 10)

     (testing "Second run finds only granules created since the last notification"
       (let [gran2 (data-core/ingest "PROV1"
                                     (data-granule/granule-with-umm-spec-collection coll1
                                                                                    (:concept-id coll1)
                                                                                    {:granule-ur "Granule2"
                                                                                     :access-value 1})
                                     {:token "mock-echo-system-token"})
             _ (index/wait-until-indexed)
             result (trigger-process-subscriptions)
             response (->> result
                           (map #(nth % 1))
                           flatten
                           (map :concept-id))]
         (is (= [(:concept-id gran2)] response)))))))

(deftest ^:oracle subscription-query-ingest-test
  (system/only-with-real-database
   (testing
    "Test subscriptions which uses a query containing 'options'. These options
     may or may not be URL encoded and the ingest needs to take this into account
     before processing the subscriptions. See CMR-7071."
     (let [user2-group-id (echo-util/get-or-create-group (system/context) "group2")
           _user2-token (echo-util/login (system/context) "user2" [user2-group-id])
           _ (echo-util/ungrant (system/context)
                                (-> (access-control/search-for-acls (system/context)
                                                                    {:provider "PROV1"
                                                                     :identity-type "catalog_item"}
                                                                    {:token "mock-echo-system-token"})
                                    :items
                                    first
                                    :concept_id))
           _ (echo-util/grant (system/context)
                              [{:group_id user2-group-id :permissions [:read]}]
                              :catalog_item_identity
                              {:provider_id "PROV1"
                               :name "Provider collection/granule ACL- test query encodings"
                               :collection_applicable true
                               :granule_applicable true
                               :granule_identifier {:access_value {:include_undefined_value true
                                                                   :min_value 1 :max_value 50}}})
           _ (ac-util/wait-until-indexed)
           _ (trigger-process-subscriptions)
           expected "provider=PROV1&options[spatial][or]=true"
           coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll1"
                                                                               :EntryTitle "entry-title1"})
                                                       {:token "mock-echo-system-token"})

           _ (index/wait-until-indexed)
           sub2 (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                        {:Name "test_sub_prov1-encoded-option"
                                                         :SubscriberId "user2"
                                                         :CollectionConceptId (:concept-id coll1)
                                                         :Query expected})
                                                       {:token "mock-echo-system-token"})
           sub3 (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                        {:Name "test_sub_prov1-raw-option"
                                                         :SubscriberId "user2"
                                                         :CollectionConceptId (:concept-id coll1)
                                                         :Query "provider=PROV1&options%5bspatial%5d%5bor%5d=true"})
                                                       {:token "mock-echo-system-token"})
           _ (index/wait-until-indexed)

           gran3 (data-core/ingest "PROV1"
                                   (data-granule/granule-with-umm-spec-collection coll1
                                                                                  (:concept-id coll1)
                                                                                  {:granule-ur "Granule3"
                                                                                   :access-value 1})
                                   {:token "mock-echo-system-token"})
           _ (index/wait-until-indexed)
           gran3-concept-id (:concept-id gran3)
           response (trigger-process-subscriptions)
           results-as-hash (process-result->hash response)
           result-1 (first results-as-hash)
           result-2 (second results-as-hash)
           ]
       ;; Was the query decoded and stored correctly and was the granule found
       (is (= (:concept-id gran3) (-> result-1 :subscriber-filtered-gran-refs first :concept-id)))
       (is (= (:concept-id gran3) (-> result-2 :subscriber-filtered-gran-refs first :concept-id)))
       (is (= expected (-> result-1 result->query)))
       (is (= expected (-> result-2 result->query)))))))
