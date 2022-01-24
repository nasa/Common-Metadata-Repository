(ns cmr.system-int-test.ingest.subscription-processing-test
  "CMR subscription processing tests."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.ingest.services.subscriptions-helper :as jobs]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as data-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.subscription-util :as subscription-util]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.metadata-db :as mdb2]))

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

(defn- trigger-process-subscriptions
  "Sets up process-subscriptions arguments. Calls process-subscriptions, returns granule concept-ids."
  ([]
   (trigger-process-subscriptions (system/context)))
  ([system-context]
   (let [subscriptions (->> (mdb2/find-concepts (system/context) {:latest true} :subscription)
                            (remove :deleted)
                            (mapv #(select-keys % [:concept-id :extra-fields :metadata])))]
     (#'jobs/process-subscriptions system-context subscriptions))))

(defn- create-granule-and-index
  "A utility function to reduce common code in these tests, Create a test granule and then wait for it to be indexed."
  [provider collection granule-ur]
  (let [concept-id (:concept-id collection)
        attribs {:granule-ur granule-ur :access-value 1}
        gran (data-granule/granule-with-umm-spec-collection collection concept-id attribs)
        options {:token "mock-echo-system-token"}
        result (data-core/ingest provider gran options)]
    (index/wait-until-indexed)
    result))

(defn- mock-send-subscription-emails
  [context subscriber-filtered-gran-refs-list]
  (let
   [send-update-subscription-notification-time! #'jobs/send-update-subscription-notification-time!]
    (doseq [subscriber-filtered-gran-refs-tuple subscriber-filtered-gran-refs-list
            :let [[sub-id subscriber-filtered-gran-refs subscriber-id subscription] subscriber-filtered-gran-refs-tuple]]
      (when (seq subscriber-filtered-gran-refs)
        (send-update-subscription-notification-time! context sub-id)))
    subscriber-filtered-gran-refs-list))

(deftest  subscription-job-manual-time-constraint-test
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
        ;;  Create coll1 granules
         coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                     (data-umm-c/collection {:ShortName "coll1"
                                                                             :EntryTitle "entry-title1"})
                                                     {:token "mock-echo-system-token"})
         coll1-concept-id (:concept-id coll1)
         c1g1 (data-granule/granule-with-umm-spec-collection
               coll1 coll1-concept-id {:day-night "DAY"})
         c1g2 (data-granule/granule-with-umm-spec-collection
               coll1 coll1-concept-id {:day-night "DAY"})
         c1g3 (data-granule/granule-with-umm-spec-collection
               coll1 coll1-concept-id {:day-night "DAY"})
         c1g4 (data-granule/granule-with-umm-spec-collection
               coll1 coll1-concept-id {:day-night "DAY"})
         c1g5 (data-granule/granule-with-umm-spec-collection
               coll1 coll1-concept-id {:day-night "NIGHT"})
         c1g6 (data-granule/granule-with-umm-spec-collection
               coll1 coll1-concept-id {:day-night "NIGHT"})
         coll1_granule1 (subscription-util/save-umm-granule "PROV1" c1g1 {:revision-date "2016-01-01T10:00:00Z"})
         coll1_granule2 (subscription-util/save-umm-granule "PROV1" c1g2 {:revision-date "2016-01-02T10:00:00Z"})
         coll1_granule3 (subscription-util/save-umm-granule "PROV1" c1g3 {:revision-date "2016-01-03T10:00:00Z"})
         coll1_granule4 (subscription-util/save-umm-granule "PROV1" c1g4 {:revision-date "2016-01-04T10:00:00Z"})
         _coll1_granule5 (subscription-util/save-umm-granule "PROV1" c1g5 {:revision-date "2016-01-01T10:00:00Z"})
         _coll1_granule6 (subscription-util/save-umm-granule "PROV1" c1g6 {:revision-date "2016-01-02T10:00:00Z"})
        ;;  Create coll2 granules
         coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                     (data-umm-c/collection {:ShortName "coll2"
                                                                             :EntryTitle "entry-title2"})
                                                     {:token "mock-echo-system-token"})
         coll2-concept-id (:concept-id coll2)
         c2g1 (data-granule/granule-with-umm-spec-collection
               coll2 coll2-concept-id {:day-night "DAY"})
         c2g2 (data-granule/granule-with-umm-spec-collection
               coll2 coll2-concept-id {:day-night "DAY"})
         c2g3 (data-granule/granule-with-umm-spec-collection
               coll2 coll2-concept-id {:day-night "DAY"})
         c2g4 (data-granule/granule-with-umm-spec-collection
               coll2 coll2-concept-id {:day-night "DAY"})
         _coll2_granule1 (subscription-util/save-umm-granule "PROV1" c2g1 {:revision-date "2015-12-30T10:00:00Z"})
         _coll2_granule2 (subscription-util/save-umm-granule "PROV1" c2g2 {:revision-date "2015-12-31T10:00:00Z"})
         _coll2_granule3 (subscription-util/save-umm-granule "PROV1" c2g3 {:revision-date "2016-01-05T10:00:00Z"})
         _coll2_granule4 (subscription-util/save-umm-granule "PROV1" c2g4 {:revision-date "2016-01-06T10:00:00Z"})
         _ (index/wait-until-indexed)
         ;; Setup subscriptions
         _sub1 (subscription-util/create-subscription-and-index coll1 "test_sub_prov1_coll1" "user2" "day_night_flag=day")
         _sub2 (subscription-util/create-subscription-and-index coll2 "test_sub_prov1_coll2" "user2" "day_night_flag=day")]

     (testing "given a time constraint with no granules..."
       (let [time-constraint "2016-01-07T00:00:00Z,2016-01-09T00:00:00Z"
             system-context (merge (system/context) {:time-constraint time-constraint})
             result (->> system-context
                         (trigger-process-subscriptions)
                         (first)
                         (second))]
         (is (= (count result) 0))))

     (testing "given a time constraint with only an end time, get all granules until the end time"
       (let [time-constraint ",2016-01-04T00:00:00Z"
             system-context (merge (system/context) {:time-constraint time-constraint})
             result (->> system-context
                         (trigger-process-subscriptions)
                         (first)
                         (second))]
         (is (= (count result) 3))
         (is (= (:concept-id coll1_granule1) (:concept-id (nth result 0))))
         (is (= (:concept-id coll1_granule2) (:concept-id (nth result 1))))
         (is (= (:concept-id coll1_granule3) (:concept-id (nth result 2))))))

     (testing "given a time constraint with only a start time, get all granules from start time until now"
       (let [time-constraint "2016-01-02T00:00:00Z,"
             system-context (merge (system/context) {:time-constraint time-constraint})
             result (->> system-context
                         (trigger-process-subscriptions)
                         (first)
                         (second))]
         (is (= (count result) 3))
         (is (= (:concept-id coll1_granule2) (:concept-id (nth result 0))))
         (is (= (:concept-id coll1_granule3) (:concept-id (nth result 1))))
         (is (= (:concept-id coll1_granule4) (:concept-id (nth result 2))))))

     (testing "given a time constraing with a start and end time, get the granules that fall within the time constraint"
       (let [time-constraint "2016-01-02T00:00:00Z,2016-01-04T00:00:00Z"
             system-context (merge (system/context) {:time-constraint time-constraint})
             result (->> system-context
                         (trigger-process-subscriptions)
                         (first)
                         (second))]
         (is (= (count result) 2))
         (is (= (:concept-id coll1_granule2) (:concept-id (first result))))
         (is (= (:concept-id coll1_granule3) (:concept-id (second result)))))))))

(deftest subscription-email-processing-time-constraint-test
  (system/only-with-real-database
   (with-redefs
    [jobs/send-subscription-emails mock-send-subscription-emails]
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

         ;; Setup collection
           coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll1"
                                                                               :EntryTitle "entry-title1"})
                                                       {:token "mock-echo-system-token"})

           coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll2"
                                                                               :EntryTitle "entry-title2"})
                                                       {:token "mock-echo-system-token"})
           _ (index/wait-until-indexed)
         ;; Setup subscriptions
           sub1 (subscription-util/create-subscription-and-index coll1 "test_sub_prov1" "user2" "provider=PROV1")]

       (testing "First query executed does not have a last-notified-at and looks back 24 hours"
         (let [gran1 (create-granule-and-index "PROV1" coll1 "Granule1")
               results (->> (jobs/email-subscription-processing (system/context))
                            (map #(nth % 1))
                            flatten
                            (map :concept-id))]
           (is (= (:concept-id gran1) (first results)))))

       (dev-system/advance-time! 10)

       (testing "Second run finds only collections created since the last notification"
         (let [gran2 (create-granule-and-index "PROV1" coll1 "Granule2")
               response (->> (jobs/email-subscription-processing (system/context))
                             (map #(nth % 1))
                             flatten
                             (map :concept-id))]
           (is (= [(:concept-id gran2)] response))))

       (testing "Deleting the subscription purges the subscription notification entry"
       ;; Delete and reingest the subscription. If the sub-notification was purged, then it
       ;; should look back 24 hours, as if the subscription were new.
         (let [concept {:provider-id "PROV1" :concept-type :subscription :native-id "test_sub_prov1"}]
           (ingest/delete-concept concept))
         (subscription-util/create-subscription-and-index coll1 "test_sub_prov1" "user2" "provider=PROV1")
         (is (= 2
                (->> (jobs/email-subscription-processing (system/context))
                     (map #(nth % 1))
                     flatten
                     (map :concept-id)
                     count))))))))


(deftest redef-test
  (testing "test1"
    (with-redefs
     [jobs/send-subscription-emails mock-send-subscription-emails]
      (jobs/email-subscription-processing (system/context)))))