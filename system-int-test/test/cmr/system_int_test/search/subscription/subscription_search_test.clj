(ns cmr.system-int-test.search.subscription.subscription-search-test
  "This tests searching subscriptions."
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.subscription-util :as subscriptions]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" 
                                       "provguid2" "PROV2" 
                                       "provguid3" "PROV3"
                                       "provguid4" "PROV4"})
                (subscriptions/grant-all-subscription-fixture
                  {"provguid1" "PROV1" "provguid2" "PROV2"} [:read :update] [:read :update])
                ;; No read permission granted for guest for PROV3
                (subscriptions/grant-all-subscription-fixture
                  {"provguid3" "PROV3"} [:update] [:read :update])
                ;; No read permission granted for any user_types for PROV4.
                (subscriptions/grant-all-subscription-fixture
                  {"provguid4" "PROV4"} [:update] [:update])]))

(deftest search-for-subscriptions-user-read-permission-test
  ;; EMAIL_SUBSCRIPTION_MANAGEMENT ACL grants only update permission for guest on PROV3,
  ;; so subscription for PROV3 can be ingested but can't be searched by guest.
  ;; but it's searchable by registered users because read permission is granted.
  (let [subscription3 (subscriptions/ingest-subscription-with-attrs {:native-id "Sub3"
                                                                     :Name "Subscription3"
                                                                     :SubscriberId "SubId3"
                                                                     :CollectionConceptId "C1-PROV3"
                                                                     :provider-id "PROV3"})
        guest-token (echo-util/login-guest (system/context))
        user1-token (echo-util/login (system/context) "user1")]
    (index/wait-until-indexed)
    (is (= 201 (:status subscription3)))
    (are3 [expected-subscriptions query]
      (do
        (testing "JSON format"
          (subscriptions/assert-subscription-search expected-subscriptions (subscriptions/search-json query))))

      "Find all returns nothing for guest"
      [] {}

      "Find all returns all for registered user"
      [subscription3] {:token user1-token})))

(deftest search-for-subscriptions-group-read-permission-test
  (let [subscription4 (subscriptions/ingest-subscription-with-attrs {:native-id "Sub4"
                                                                     :Name "Subscription4"
                                                                     :SubscriberId "SubId4"
                                                                     :CollectionConceptId "C1-PROV4"
                                                                     :provider-id "PROV4"})
        ;; create two groups
        group1-concept-id (echo-util/get-or-create-group (system/context) "group1")
        group2-concept-id (echo-util/get-or-create-group (system/context) "group2")
        ;; create two user tokens, associated with two groups
        user1g-token (echo-util/login (system/context) "user1g" [group1-concept-id])
        user2g-token (echo-util/login (system/context) "user2g" [group2-concept-id])]
    (index/wait-until-indexed)

    ;; grant one group read permission
    (echo-util/grant-all-subscription-group-esm (system/context) "PROV4" group1-concept-id [:read])
    
    (is (= 201 (:status subscription4)))
    (are3 [expected-subscriptions query]
      (do
        (testing "JSON format"
          (subscriptions/assert-subscription-search expected-subscriptions (subscriptions/search-json query))))

      "Find all returns nothing for user2g"
      [] {:token user2g-token}

      "Find all returns all for user1g"
      [subscription4] {:token user1g-token})))
