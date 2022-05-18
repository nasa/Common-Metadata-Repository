(ns cmr.system-int-test.ingest.subscription-ingest-permission-test
  "CMR subscription ingest permission integration tests.
  For some granule subscription permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac-util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.subscription-util :as subscription-util]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture
                 {"provguid1" "PROV1" "provguid2" "PROV2"})]))

(deftest subscription-ingest-permission-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (let [reg-user-group (echo-util/get-or-create-group (system/context) "some-group-guid")
        user1-token (echo-util/login (system/context) "user1" [reg-user-group])
        guest-token (echo-util/login-guest (system/context))
        coll-sub-group (echo-util/get-or-create-group (system/context) "coll-sub-group")
        coll-sub-token (echo-util/login (system/context) "coll-sub-user" [coll-sub-group])
        _ (echo-util/grant-group-tag (system/context) coll-sub-group :update)
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        coll2 (data-core/ingest-umm-spec-collection
               "PROV2"
               (data-umm-c/collection
                {:ShortName "coll2"
                 :EntryTitle "entry-title2"})
               {:token user1-token})

        gran-sub1 (subscription-util/make-subscription-concept
                   {:Type "granule"
                    :CollectionConceptId (:concept-id coll1)}
                   {}
                   "gran-sub1")
        gran-sub2 (subscription-util/make-subscription-concept
                   {:Type "granule"
                    :CollectionConceptId (:concept-id coll2)}
                   {}
                   "gran-sub2")
        coll-sub (subscription-util/make-subscription-concept
                  {:Type "collection"}
                  {}
                  "coll-sub")]

    (testing "create a granule subscription without permission"
      (let [{:keys [status errors]} (ingest/ingest-concept gran-sub1 {:token user1-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "create a granule subscription with permission on PROV1"
      ;; grant registered user permission to PROV1
      (echo-util/grant-all-subscription-sm (system/context)
                                           "PROV1"
                                           [:read]
                                           [:read :update])
      (ac-util/wait-until-indexed)
      (ingest/clear-caches)
      (let [{:keys [status errors]} (ingest/ingest-concept gran-sub1 {:token user1-token})]
        (is (= 201 status))
        (is (nil? errors)))

      (testing "create a granule subscription on PROV2 still doesn't work as no permission is granted on PROV2"
        (let [{:keys [status errors]} (ingest/ingest-concept gran-sub2 {:token user1-token})]
          (is (= 401 status))
          (is (= ["You do not have permission to perform that action."] errors)))))

    (testing "delete without permission, guest is not granted update permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [{:keys [status errors]} (ingest/delete-concept gran-sub1
                                                           {:token guest-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "delete with permission"
      (let [{:keys [status concept-id revision-id]} (ingest/delete-concept gran-sub1
                                                                           {:token user1-token})
            fetched (mdb/get-concept concept-id revision-id)]
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (= (:native-id gran-sub1)
               (:native-id fetched)))
        (is (:deleted fetched)))

      (testing "delete a granule subscription on PROV2 still doesn't work as no permission is granted on PROV2"
        (let [{:keys [status errors]} (ingest/ingest-concept gran-sub2 {:token user1-token})]
          (is (= 401 status))
          (is (= ["You do not have permission to perform that action."] errors)))))

    (testing "create collection subscription using a token that can create granule subscription does not work"
      (let [{:keys [status errors]} (ingest/ingest-concept coll-sub {:token user1-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "create collection subscription with permission"
      (let [{:keys [status errors]} (ingest/ingest-concept coll-sub {:token coll-sub-token})]
        (is (= 201 status))
        (is (nil? errors))))

    (testing "delete collection subscription without permission, using a token that can create granule subscription does not work"
      (let [{:keys [status errors]} (ingest/delete-concept coll-sub
                                                           {:token user1-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "delete with permission"
      (let [{:keys [status concept-id revision-id]} (ingest/delete-concept coll-sub
                                                                           {:token coll-sub-token})
            fetched (mdb/get-concept concept-id revision-id)]
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (= (:native-id coll-sub)
               (:native-id fetched)))
        (is (:deleted fetched))))))

;; This tests the subscription routes in ingest root url, will be removed in CMR-8270
(deftest subscription-ingest-permission-temporary-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (let [reg-user-group (echo-util/get-or-create-group (system/context) "some-group-guid")
        user1-token (echo-util/login (system/context) "user1" [reg-user-group])
        guest-token (echo-util/login-guest (system/context))
        coll-sub-group (echo-util/get-or-create-group (system/context) "coll-sub-group")
        coll-sub-token (echo-util/login (system/context) "coll-sub-user" [coll-sub-group])
        _ (echo-util/grant-group-tag (system/context) coll-sub-group :update)
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        coll2 (data-core/ingest-umm-spec-collection
               "PROV2"
               (data-umm-c/collection
                {:ShortName "coll2"
                 :EntryTitle "entry-title2"})
               {:token user1-token})

        gran-sub1 (subscription-util/make-subscription-concept
                   {:Type "granule"
                    :CollectionConceptId (:concept-id coll1)}
                   {}
                   "gran-sub1")
        gran-sub2 (subscription-util/make-subscription-concept
                   {:Type "granule"
                    :CollectionConceptId (:concept-id coll2)}
                   {}
                   "gran-sub2")
        coll-sub (subscription-util/make-subscription-concept
                  {:Type "collection"}
                  {}
                  "coll-sub")]

    (testing "create a granule subscription without permission"
      (let [{:keys [status errors]} (ingest/ingest-subscription-concept
                                     gran-sub1 {:token user1-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "create a granule subscription with permission on PROV1"
      ;; grant registered user permission to PROV1
      (echo-util/grant-all-subscription-sm (system/context)
                                           "PROV1"
                                           [:read]
                                           [:read :update])
      (ac-util/wait-until-indexed)
      (ingest/clear-caches)
      (let [{:keys [status errors]} (ingest/ingest-subscription-concept
                                     gran-sub1 {:token user1-token})]
        (is (= 201 status))
        (is (nil? errors)))

      (testing "create a granule subscription on PROV2 still doesn't work as no permission is granted on PROV2"
        (let [{:keys [status errors]} (ingest/ingest-subscription-concept
                                       gran-sub2 {:token user1-token})]
          (is (= 401 status))
          (is (= ["You do not have permission to perform that action."] errors)))))

    (testing "delete without permission, guest is not granted update permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [{:keys [status errors]} (ingest/delete-subscription-concept
                                     gran-sub1
                                     {:token guest-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "delete with permission"
      (let [{:keys [status concept-id revision-id]} (ingest/delete-subscription-concept
                                                     gran-sub1
                                                     {:token user1-token})
            fetched (mdb/get-concept concept-id revision-id)]
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (= (:native-id gran-sub1)
               (:native-id fetched)))
        (is (:deleted fetched)))

      (testing "delete a granule subscription on PROV2 still doesn't work as no permission is granted on PROV2"
        (let [{:keys [status errors]} (ingest/ingest-subscription-concept
                                       gran-sub2 {:token user1-token})]
          (is (= 401 status))
          (is (= ["You do not have permission to perform that action."] errors)))))

    (testing "create collection subscription using a token that can create granule subscription does not work"
      (let [{:keys [status errors]} (ingest/ingest-subscription-concept
                                     coll-sub {:token user1-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "create collection subscription with permission"
      (let [{:keys [status errors]} (ingest/ingest-subscription-concept
                                     coll-sub {:token coll-sub-token})]
        (is (= 201 status))
        (is (nil? errors))))

    (testing "delete collection subscription without permission, using a token that can create granule subscription does not work"
      (let [{:keys [status errors]} (ingest/delete-subscription-concept
                                     coll-sub
                                     {:token user1-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "delete with permission"
      (let [{:keys [status concept-id revision-id]} (ingest/delete-subscription-concept
                                                     coll-sub
                                                     {:token coll-sub-token})
            fetched (mdb/get-concept concept-id revision-id)]
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (= (:native-id coll-sub)
               (:native-id fetched)))
        (is (:deleted fetched))))))
