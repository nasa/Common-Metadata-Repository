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
                ;; No read permission granted for any user for PROV3
                (subscriptions/grant-all-subscription-fixture
                  {"provguid3" "PROV3"} [:update] [:update])]))

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
      [] {:token guest-token}

      "Find all returns nothing for registered user"
      [] {:token user1-token})))
