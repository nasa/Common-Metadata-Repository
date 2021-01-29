(ns cmr.system-int-test.ingest.subscription-processing-test
  "CMR subscription processing tests."
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.ingest.services.email-processing :as email-processing]
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

(defn- process-result->hash
  "Deconstruct a subscription process response from the function
    trigger-process-subscriptions into a hash table so that tests can references
    each value by name and not have to use (nth x #). The order is defined in
    email_processing.clj"
  [response]
  (map (fn [x]
         (let [[sub-id
                coll-id
                native-id
                provider-id
                subscriber-filtered-gran-refs
                email-address
                subscription
                permission-check-time
                permission-check-failed] x]
           {:sub-id sub-id
            :coll-id coll-id
            :native-id native-id
            :provider-id provider-id
            :subscriber-filtered-gran-refs subscriber-filtered-gran-refs
            :email-address email-address
            :subscription subscription
            :permission-check-time permission-check-time
            :permission-check-failed permission-check-failed})) response))

(defn- trigger-process-subscriptions
  "Sets up process-subscriptions arguments. Calls process-subscriptions, returns granule concept-ids."
  []
  (let [subscriptions (->> (mdb2/find-concepts (system/context) {:latest true} :subscription)
                           (remove :deleted)
                           (mapv #(select-keys % [:concept-id :extra-fields :metadata :provider-id :native-id])))]
    (#'email-processing/process-subscriptions (system/context) subscriptions)))

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
         sub1 (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                      {:Name "test_sub_prov1"
                                                       :SubscriberId "user2"
                                                       :CollectionConceptId (:concept-id coll1)
                                                       :Query "provider=PROV1"})
                                                     {:token "mock-echo-system-token"})]
     (index/wait-until-indexed)

     (testing "First query executed does not have a last-notified-at and looks back 1 hours"
       (let [gran1 (data-core/ingest "PROV1"
                                     (data-granule/granule-with-umm-spec-collection coll1
                                                                                    (:concept-id coll1)
                                                                                    {:granule-ur "Granule1"
                                                                                     :access-value 1})
                                     {:token "mock-echo-system-token"})
             _ (index/wait-until-indexed)
             _ (dev-system/advance-time! 10)
             results (first (process-result->hash (trigger-process-subscriptions)))
             found-granule (:concept-id (first (:subscriber-filtered-gran-refs results)))]
         (is (= (:concept-id gran1) found-granule)))) ()

     (dev-system/advance-time! 10)

     (testing "Second run finds only granules created since the last notification"
       (let [gran2 (data-core/ingest "PROV1"
                                     (data-granule/granule-with-umm-spec-collection coll1
                                                                                    (:concept-id coll1)
                                                                                    {:granule-ur "Granule2"
                                                                                     :access-value 1})
                                     {:token "mock-echo-system-token"})
             _ (index/wait-until-indexed)
             response (trigger-process-subscriptions)
             result-as-hash (first (process-result->hash response))
             _ (#'email-processing/send-subscription-emails (system/context) response)
             found-granule (first (:subscriber-filtered-gran-refs result-as-hash))]
         (is (= (:concept-id gran2) (:concept-id found-granule)))))

     (testing "Remove ACL permissions from user after 3 days delete subscription and notifiy user"
       (echo-util/ungrant-by-search (system/context)
                                    {:provider "PROV1"
                                     :identity-type "catalog_item"}
                                    "mock-echo-system-token")
       (ac-util/wait-until-indexed)

       (testing "10 second after permissions are removed"
         (dev-system/advance-time! 10)
         (let [response (first (process-result->hash (trigger-process-subscriptions)))]
           (is (= (:concept-id sub1) (:sub-id response)))
           (is (= true (:permission-check-failed response))))
         (let [subscription (subscription-util/search-json {:token "mock-echo-system-token"})]
           (is (= nil (-> subscription :items :concept-id)))))

       (testing "3 days after permissions are removed"
         (dev-system/advance-time! (* 3 24 3610))
         (let [response (trigger-process-subscriptions)
               result-as-hash (process-result->hash (trigger-process-subscriptions))]
           (#'email-processing/send-subscription-emails (system/context) response)
           (is (= (:concept-id sub1) (:sub-id (first result-as-hash))))
           (is (= true (:permission-check-failed (first result-as-hash))))
           (let [subscription (subscription-util/search-json {:token "mock-echo-system-token"})]
             (is (= nil (-> subscription :items :concept-id))))))

       (testing
         "The subscription to time constraint function is critical for internal use
          when debuging it became apparent that the proper operation of this function
          made a big difference. Test the function's two uses cases, 1) no notified
          date is specified, 2) one is specified"
        (dev-system/freeze-time! "2012-01-19T18:00:00.00Z")
        (let [empty-ish {:extra-fields {}}
              populated {:extra-fields {:last-notified-at "2012-01-10T08:00:00.000Z"}}
              time-constraint-empty (#'email-processing/subscription->time-constraint empty-ish (tk/now) 3600)
              time-constraint-populated (#'email-processing/subscription->time-constraint populated (tk/now) 3600)]
         (is (= "2012-01-19T17:00:00.000Z,2012-01-19T18:00:00.000Z" time-constraint-empty))
         (is (= "2012-01-10T08:00:00.000Z,2012-01-19T18:00:00.000Z" time-constraint-populated))
         (dev-system/clear-current-time!)))))))
