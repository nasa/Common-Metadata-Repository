(ns cmr.system-int-test.ingest.provider-ingest-permissions-test
  "Verifies the correct provider ingest permissions are enforced"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-system]
   [cmr.access-control.test.util :as ac-util]
   [cmr.system-int-test.utils.generic-util :as generic-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.service-util :as service-util]
   [cmr.system-int-test.utils.subscription-util :as subscription-util]
   [cmr.system-int-test.utils.tool-util :as tool-util]
   [cmr.system-int-test.utils.variable-util :as variable-util]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs])
   (:import
    [java.util UUID]))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                                          {:grant-all-search? false
                                           :grant-all-ingest? false})
                                    (subscription-util/grant-all-subscription-fixture {"provguid1" "PROV1"}
                                                                  [:read :update]
                                                                  [:read :update])
                                    (subscription-util/grant-all-subscription-fixture {"provguid2" "PROV2"}
                                                                  [:read]
                                                                  [:read])
                                    (dev-system/freeze-resume-time-fixture)]))

(defn- assert-ingest-succeeded
  "Succeeds if the provided token has permission to perform the given function."
  [response]
  (let [status (:status response)]
    (is (some #{status} [200 201 204 404]))))

(defn- assert-ingest-no-permission
  "Succeeds if provided token has no permission to perform the given function."
  [response]
  (let [status (:status response)]
    (is (= 401 status))))

(defn- assert-ingest-not-found
  "Succeeds if provided token returns 404 NOT FOUND error."
  [response]
  (let [status (:status response)]
    (is (= 404 status))))

(deftest ingest-provider-management-permissions-test
  (let [_ (mock-urs/create-users (s/context) [{:username "someSubId" :password "Password"}])
        prov-admin-read-group-concept-id (echo-util/get-or-create-group
                                          (s/context) "prov-admin-read-group")
        prov-admin-update-group-concept-id (echo-util/get-or-create-group
                                            (s/context)
                                            "prov-admin-update-group")
        prov-admin-read-update-group-concept-id (echo-util/get-or-create-group
                                                 (s/context)
                                                 "prov-admin-read-update-group")
        prov-admin-update-delete-group-concept-id (echo-util/get-or-create-group
                                                   (s/context)
                                                   "prov-admin-update-delete-group")
        prov-admin-read-update-delete-group-concept-id (echo-util/get-or-create-group
                                                        (s/context)
                                                        "prov-admin-read-update-delete-group")
        another-prov-admin-group-concept-id (echo-util/get-or-create-group
                                             (s/context) "another-prov-admin-group")
        ingest-super-admin-group-concept-id (echo-util/get-or-create-group
                                             (s/context) "ingest-super-admin-group")
        plain-group-concept-id2 (echo-util/get-or-create-group (s/context) "plain-group-2")
        plain-group-concept-id3 (echo-util/get-or-create-group (s/context) "plain-group-3")
        guest-token (echo-util/login-guest (s/context))
        user-token (echo-util/login
                    (s/context)
                    "user1"
                    [plain-group-concept-id2 plain-group-concept-id3])
        provider-admin-read-token (echo-util/login
                                   (s/context)
                                   "prov-admin-read"
                                   [prov-admin-read-group-concept-id
                                    plain-group-concept-id3])
        provider-admin-update-token (echo-util/login
                                     (s/context)
                                     "prov-admin-update"
                                     [prov-admin-update-group-concept-id
                                      plain-group-concept-id3])
        provider-admin-read-update-token (echo-util/login
                                          (s/context) "prov-admin-read-update"
                                          [prov-admin-read-update-group-concept-id
                                           plain-group-concept-id3])
        provider-admin-update-delete-token (echo-util/login
                                            (s/context) "prov-admin-update-delete"
                                            [prov-admin-update-delete-group-concept-id
                                             plain-group-concept-id3])
        another-prov-admin-token (echo-util/login
                                  (s/context)
                                  "another-prov-admin"
                                  [another-prov-admin-group-concept-id
                                   plain-group-concept-id3])
        super-admin-token (echo-util/login
                           (s/context)
                           "super-admin"
                           [ingest-super-admin-group-concept-id])
        non-existant-token "not-exist"

        ;; Grant provider admin permission
        _ (echo-util/grant-group-provider-admin
           (s/context) prov-admin-read-group-concept-id "PROV1" :read)
        _ (echo-util/grant-group-provider-admin
           (s/context) prov-admin-update-group-concept-id "PROV1" :update)
        _ (echo-util/grant-group-provider-admin
           (s/context) prov-admin-update-group-concept-id "PROV2" :update)
        _ (echo-util/grant-group-provider-admin
           (s/context)
           prov-admin-read-update-group-concept-id
           "PROV1"
           :read
           :update)
        _ (echo-util/grant-group-provider-admin
           (s/context)
           prov-admin-read-update-group-concept-id
           "PROV2"
           :read
           :update)
        _ (echo-util/grant-group-provider-admin
           (s/context)
           prov-admin-update-delete-group-concept-id
           "PROV1"
           :delete
           :update)
        _ (echo-util/grant-group-provider-admin
           (s/context)
           prov-admin-update-delete-group-concept-id
           "PROV2"
           :delete
           :update)
        ;; Grant provider admin permission but for a different provider
        _ (echo-util/grant-group-provider-admin
           (s/context)
           another-prov-admin-group-concept-id
           "PROV2"
           :read
           :update
           :delete)
        ;; Grant system admin permission - but not provider admin
        _ (echo-util/grant-group-admin
           (s/context)
           ingest-super-admin-group-concept-id
           :read
           :update
           :delete)

        collection (d/ingest-umm-spec-collection
                    "PROV1"
                    (data-umm-c/collection {})
                    {:token provider-admin-update-token})
        coll2 (d/ingest-umm-spec-collection
                    "PROV2"
                    (data-umm-c/collection {})
                    {:token provider-admin-update-token})
        ingested-concept (mdb/get-concept (:concept-id collection))

        granule (d/item->concept
                 (dg/granule-with-umm-spec-collection collection "C1-PROV1"))
        service (service-util/make-service-concept)

        subscription (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id collection)})
        ;; create a subscription on PROV2, which doesn't have SUBSCRIPTION_MANAGEMENT permission
        ;; to ingest. So all the ingests should fail.
        subscription-np (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll2)
                                                                      :provider-id "PROV2"} {} 1)
        tool (tool-util/make-tool-concept)

        ;; Generic Document Pipeline
        grid generic-util/grid-good
        grid-native-id (format "Generic-Test-%s" (UUID/randomUUID))]

    (testing "ingest granule update permissions"
      (are [token]
           (assert-ingest-succeeded
            (ingest/ingest-concept granule {:token token
                                            :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)

      (are [token]
           (assert-ingest-no-permission
            (ingest/ingest-concept granule {:token token
                                            :allow-failure? true}))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest granule delete permissions"
      (are [token]
           (assert-ingest-succeeded
            (ingest/delete-concept granule {:token token
                                            :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (assert-ingest-no-permission
            (ingest/delete-concept granule {:token token
                                            :allow-failure? true}))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest collection update permissions"
      (are [token]
           (assert-ingest-succeeded
            (d/ingest-umm-spec-collection
              "PROV1"
              (data-umm-c/collection {})
              {:token token
               :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (assert-ingest-no-permission
            (d/ingest-umm-spec-collection
             "PROV1"
             (data-umm-c/collection {})
             {:token token
              :allow-failure? true}))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest collection delete permissions"
      (are [token]
           (assert-ingest-succeeded
            (ingest/delete-concept ingested-concept {:token token
                                                     :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (assert-ingest-no-permission
            (ingest/delete-concept ingested-concept {:token token
                                                     :allow-failure? true}))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest service update permissions"
      (are [token]
           (assert-ingest-succeeded
            (ingest/ingest-concept service {:token token
                                            :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)

      (are [token]
           (assert-ingest-no-permission
            (ingest/ingest-concept service {:token token
                                            :allow-failure? true}))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest tool update permissions"
      (are3 [token]
            (assert-ingest-succeeded
             (ingest/ingest-concept tool {:token token
                                          :allow-failure? true}))
            "provider-admin-update-token can ingest"
            provider-admin-update-token
            "provider-admin-read-update token can ingest"
            provider-admin-read-update-token
            "provider-admin-update-delete token can ingest"
            provider-admin-update-delete-token)

      (are3 [token]
            (assert-ingest-no-permission
             (ingest/ingest-concept tool {:token token
                                          :allow-failure? true}))
            "guest-token can't ingest"
            guest-token
            "user-token can't ingest"
            user-token
            "super-admin-token can't ingest"
            super-admin-token
            "another-prov-admin-token can't ingest"
            another-prov-admin-token
            "provider-admin-read-token can't ingest"
            provider-admin-read-token))

     ;; Generic Document Pipeline
     (testing "ingest generic (grid) update permissions"
       (are3 [token]
             (assert-ingest-succeeded
              (generic-util/generic-request token "PROV1" grid-native-id "grid" grid :post))
             "provider-admin-update-token can ingest"
             provider-admin-update-token
             "provider-admin-read-update-token can ingest"
             provider-admin-read-update-token
             "provider-admin-update-delete-token can ingest"
             provider-admin-update-delete-token)

       (are3 [token]
             (assert-ingest-no-permission
              (generic-util/generic-request token "PROV1" grid-native-id "grid" grid :post))
             "guest-token can't ingest"
             guest-token
             "user-token can't ingest"
             user-token
             "super-admin-token can't ingest"
             super-admin-token
             "another-prov-admin-token can't ingest"
             another-prov-admin-token
             "provider-admin-read-token can't ingest"
             provider-admin-read-token))

     ;; Ingest and delete of subscriptions are controlled by both INGEST_MANAGEMENT_ACL and SUBSCRIPTION+MANAGEMENT ACLs.
     ;; subscriptoin-np is ingested on PROV2, which has no SUBSCRIPTION_MANAGEMENT permission to ingest. so, even though
     ;; it has the INGEST_MANAGEMENT_ACL permissions, it still fails the ingest and delete.
     (testing "ingest subscription with INGEST_MANAGEMENT_ACL permission, but without SUBSCRIPTION_MANAGEMENT permission."
      (are3 [token]
            (assert-ingest-no-permission
             (ingest/ingest-concept subscription-np {:token token
                                                     :allow-failure? true}))
            "provider-admin-update-token can not ingest"
            provider-admin-update-token
            "provider-admin-read-update token can not ingest"
            provider-admin-read-update-token
            "provider-admin-update-delete token can not ingest"
            provider-admin-update-delete-token)

      (are3 [token]
            (assert-ingest-not-found
             (ingest/delete-concept subscription-np {:token token
                                                     :allow-failure? true}))
            "provider-admin-update-token can not delete"
            provider-admin-update-token
            "provider-admin-read-update token can not delete"
            provider-admin-read-update-token
            "provider-admin-update-delete token can not delete"
            provider-admin-update-delete-token))

    ;; The assert-ingest-succeeded tests below are identical to the tests above,
    ;; except that the subscription is on PROV1, which has both the INGEST and SUBSCRIPTION ACL permissions.
    ;; so both ingest-concept and delete-concept succeeded.
    (testing "ingest subscription update permissions"
      (echo-util/grant-all (s/context)
                           (echo-util/coll-catalog-item-id "PROV1"))
      (ac-util/wait-until-indexed)
      (are3 [token]
            (assert-ingest-succeeded
             (ingest/ingest-concept subscription {:token token
                                                  :allow-failure? true}))
            "provider-admin-update-token can ingest"
            provider-admin-update-token
            "provider-admin-read-update token can ingest"
            provider-admin-read-update-token
            "provider-admin-update-delete token can ingest"
            provider-admin-update-delete-token)

      (are3 [token]
            (assert-ingest-no-permission
             (ingest/ingest-concept subscription {:token token
                                                  :allow-failure? true}))
            "gest-token can't ingest"
            guest-token
            "user-token can't ingest"
            user-token
            "super-admin-token can't ingest"
            super-admin-token
            "another-prov-admin-token can't ingest"
            another-prov-admin-token
            "provider-admin-read-token can't ingest"
            provider-admin-read-token)

      (are3 [token]
            (assert-ingest-no-permission
             (ingest/delete-concept subscription {:token token
                                                  :allow-failure? true}))
            "gest-token can't delete"
            guest-token
            "user-token can't delete"
            user-token
            "super-admin-token can't delete"
            super-admin-token
            "another-prov-admin-token can't delete"
            another-prov-admin-token
            "provider-admin-read-token can't delete"
            provider-admin-read-token)

      (are3 [token]
            (assert-ingest-succeeded
             (ingest/delete-concept subscription {:token token
                                                  :allow-failure? true}))
            "provider-admin-update-token can delete"
            provider-admin-update-token
            "provider-admin-read-update token can delete"
            provider-admin-read-update-token
            "provider-admin-update-delete token can delete"
            provider-admin-update-delete-token))

    (testing "token expiration"
      ;; 29 days after token creation is OK since the token expires in 30 days
      (dev-system/advance-time! (* 29 24 3600))
      (is (assert-ingest-succeeded
           (d/ingest-umm-spec-collection
            "PROV1"
            (data-umm-c/collection {})
            {:token provider-admin-update-token
             :allow-failure? true})))
      ;; after 30 days, the token is expired
      (dev-system/advance-time! (* 2 24 3600))
      (let [{:keys [status errors]} (d/ingest-umm-spec-collection
                                     "PROV1"
                                     (data-umm-c/collection {})
                                     {:token provider-admin-update-token
                                      :allow-failure? true})]
        (is (= 401 status))
        (is (= ["Token [XXX] has expired. Note the token value has been partially redacted."] errors))))))

(deftest variable-ingest-permissions-test
  (testing "Variable ingest permissions:"
    (let [{guest-token :token} (variable-util/setup-guest-acl
                                "umm-var-guid1" "umm-var-user1")
          {registered-token :token} (variable-util/setup-guest-acl
                                     "umm-var-guid2" "umm-var-user2")
          {update-token :token} (variable-util/setup-update-acl
                                 (s/context) "PROV1")
          coll-concept-id (->> {:token update-token}
                               (d/ingest "PROV1" (dc/collection))
                               :concept-id)
          _ (index/wait-until-indexed)
          concept (variable-util/make-variable-concept
                    {:Name "Variable1"}
                    {:native-id "var1"
                     :coll-concept-id coll-concept-id})]
      (testing "disallowed create responses:"
        (are3 [token expected]
          (let [response (variable-util/ingest-variable-with-association
                           concept
                           {:token token})]
            (is (= expected (:status response))))
          "no token provided"
          nil 401
          "guest user denied"
          guest-token 401
          "regular user denied"
          registered-token 401)
       (testing "disallowed delete responses:"
        (are3 [token expected]
          (let [response (ingest/delete-concept
                          concept
                          (merge variable-util/default-opts {:token token}))]
            (is (= expected (:status response)))
            (is (string/includes?
                 "You do not have permission to perform that action."
                 (first (:errors response)))))
          "no token provided"
          nil 401
          "guest user denied"
          guest-token 401
          "regular user denied"
          registered-token 401)))
      (testing "allowed responses:"
        (let [create-response (variable-util/ingest-variable-with-association
                                concept)
              delete-response (ingest/delete-concept concept {:token update-token})]
          (testing "create variable status"
            (is (= 201 (:status create-response))))
          (testing "update variable status"
            (is (= 200 (:status delete-response)))))))))
