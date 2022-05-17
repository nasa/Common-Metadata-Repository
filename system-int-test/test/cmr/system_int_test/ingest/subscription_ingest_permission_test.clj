(ns cmr.system-int-test.ingest.subscription-ingest-permission-test
  "CMR subscription ingest permission integration tests.
  For some granule subscription permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac-util]
   [cmr.common.util :refer [are3]]
   [cmr.common-app.test.side-api :as side]
   [cmr.ingest.services.subscriptions-helper :as jobsub]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as data-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.subscription-util :as subscription-util]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.metadata-db :as mdb2]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"})
    (subscription-util/grant-all-subscription-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2"}
     [:read]
     [:read :update])
    (dev-sys-util/freeze-resume-time-fixture)
    (subscription-util/grant-all-subscription-fixture {"provguid1" "PROV3"}
                                                      [:read]
                                                      [:read :update])]))

(deftest delete-collection-subscription-ingest-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (testing "delete a collection subscription"
    (let [coll1 (data-core/ingest-umm-spec-collection
                 "PROV1"
                 (data-umm-c/collection
                  {:ShortName "coll1"
                   :EntryTitle "entry-title1"})
                 {:token "mock-echo-system-token"})
          user1-token (echo-util/login (system/context) "user1")
          guest-token (echo-util/login-guest (system/context))
          concept (subscription-util/make-subscription-concept
                   {:Type "collection"}
                   {}
                   "coll-sub")]
      (ingest/ingest-concept concept {:token user1-token})
      (testing "delete without permission, guest is not granted update permission for SUBSCRIPTION_MANAGEMENT ACL"
        (let [{:keys [status errors]} (ingest/delete-concept concept
                                                             {:token guest-token})]
          (is (= 401 status))
          (is (= ["You do not have permission to perform that action."] errors))))
      (testing "delete a collection subscription with permission"
        (let [{:keys [status concept-id revision-id]} (ingest/delete-concept concept
                                                                             {:token user1-token})
              fetched (mdb/get-concept concept-id revision-id)]
          (is (= 200 status))
          (is (= 2 revision-id))
          (is (= (:native-id concept)
                 (:native-id fetched)))
          (is (:deleted fetched))
          (testing "delete a deleted collection subscription"
            (let [{:keys [status errors]} (ingest/delete-concept concept
                                                                 {:token user1-token})]
              (is (= 404 status))
              (is (= [(format "Subscription with native-id [%s] has already been deleted."
                              (:native-id concept))]
                     errors)))))))))
