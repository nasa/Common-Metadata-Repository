(ns cmr.system-int-test.ingest.subscription-ingest-test
  "CMR subscription ingest integration tests.
  For subscription permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac-util]
   [cmr.common.util :refer [are3]]
   [cmr.ingest.services.jobs :as jobs]
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
   [cmr.transmit.metadata-db :as mdb2]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"})
    (subscription-util/grant-all-subscription-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2"}
     [:read :update]
     [:read :update])
    (dev-sys-util/freeze-resume-time-fixture)
    (subscription-util/grant-all-subscription-fixture {"provguid1" "PROV3"}
                                                      [:read]
                                                      [:read :update])]))

(defn- process-subscriptions
  "Sets up process-subscriptions arguments. Calls process-subscriptions, returns granule concept-ids."
  []
  (let [subscriptions (->> (mdb2/find-concepts (system/context) {:latest true} :subscription)
                           (remove :deleted)
                           (map #(select-keys % [:concept-id :extra-fields :metadata])))]
    (#'jobs/process-subscriptions (system/context) subscriptions)))

(deftest subscription-ingest-on-prov3-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (testing "ingest on PROV3, guest is not granted ingest permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [concept (subscription-util/make-subscription-concept {:provider-id "PROV3"
                                                                  :CollectionConceptId (:concept-id coll1)})
            guest-token (echo-util/login-guest (system/context))
            response (ingest/ingest-concept concept {:token guest-token})]
        (is (= ["You do not have permission to perform that action."] (:errors response)))))
    (testing "ingest on PROV3, registered user is granted ingest permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [concept (subscription-util/make-subscription-concept {:provider-id "PROV3"
                                                                  :CollectionConceptId (:concept-id coll1)})
            user1-token (echo-util/login (system/context) "user1")
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 201 (:status response)))))))

(deftest subscription-delete-on-prov3-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (testing "delete on PROV3, guest is not granted update permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [concept (subscription-util/make-subscription-concept {:provider-id "PROV3"
                                                                  :CollectionConceptId (:concept-id coll1)})
            guest-token (echo-util/login-guest (system/context))
            response (ingest/delete-concept concept {:token guest-token})]
        (is (= ["You do not have permission to perform that action."] (:errors response)))))
    (testing "delete on PROV3, registered user is granted update permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [concept (subscription-util/make-subscription-concept {:provider-id "PROV3"
                                                                  :CollectionConceptId (:concept-id coll1)})
            user1-token (echo-util/login (system/context) "user1")
            response (ingest/delete-concept concept {:token user1-token})]
        ;; it passes the permission validation, and gets to the point where the subscription doesn't exist
        ;; since we didn't ingest it.
        (is (= 404 (:status response)))))))

(deftest subscription-ingest-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (testing "ingest of a new subscription concept"
      (let [concept (subscription-util/make-subscription-concept
                     {:CollectionConceptId (:concept-id coll1)})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))))
    (testing "ingest of a subscription concept with a revision id"
      (let [concept (subscription-util/make-subscription-concept
                     {:CollectionConceptId (:concept-id coll1)}
                     {:revision-id 5})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (= 5 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id 5))))))

;; Verify that the accept header works
(deftest subscription-ingest-accept-header-response-test
  (let [supplied-concept-id "SUB1000-PROV1"
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (testing "json response"
      (let [response (ingest/ingest-concept
                      (subscription-util/make-subscription-concept
                       {:concept-id supplied-concept-id
                        :CollectionConceptId (:concept-id coll1)})
                      {:accept-format :json
                       :raw? true})]
        (is (= {:revision-id 1
                :native-id "Name 0"
                :concept-id supplied-concept-id}
               (ingest/parse-ingest-body :json response)))))

    (testing "xml response"
      (let [response (ingest/ingest-concept
                      (subscription-util/make-subscription-concept
                       {:concept-id supplied-concept-id
                        :CollectionConceptId (:concept-id coll1)})
                      {:accept-format :xml
                       :raw? true})]
        (is (= {:revision-id 2
                :native-id "Name 0"
                :concept-id supplied-concept-id}
               (ingest/parse-ingest-body :xml response)))))))

;; Verify that the accept header works with returned errors
(deftest subscription-ingest-with-errors-accept-header-test
  (testing "json response"
    (let [concept-no-metadata (assoc (subscription-util/make-subscription-concept)
                                     :metadata "")
          response (ingest/ingest-concept
                    concept-no-metadata
                    {:accept-format :json
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)]
      (is (re-find #"Request content is too short." (first errors)))))
  (testing "xml response"
    (let [concept-no-metadata (assoc (subscription-util/make-subscription-concept)
                                     :metadata "")
          response (ingest/ingest-concept
                    concept-no-metadata
                    {:accept-format :xml
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Request content is too short." (first errors))))))

;; Verify that user-id is saved from User-Id or token header
(deftest subscription-ingest-user-id-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (testing "ingest of new concept"
      (are3 [ingest-headers expected-user-id]
        (let [concept (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll1)})
              {:keys [concept-id revision-id]} (ingest/ingest-concept concept ingest-headers)]
          (ingest/assert-user-id concept-id revision-id expected-user-id))

        "user id from token"
        {:token (echo-util/login (system/context) "user1")} "user1"

        "user id from user-id header"
        {:user-id "user2"} "user2"

        "both user-id and token in the header results in the revision getting user id from user-id header"
        {:token (echo-util/login (system/context) "user3")
         :user-id "user4"} "user4"

        "neither user-id nor token in the header"
        {} nil))
    (testing "update of existing concept with new user-id"
      (are3 [ingest-header1 expected-user-id1
             ingest-header2 expected-user-id2
             ingest-header3 expected-user-id3
             ingest-header4 expected-user-id4]
        (let [concept (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll1)})
              {:keys [concept-id revision-id]} (ingest/ingest-concept concept ingest-header1)]
          (ingest/ingest-concept concept ingest-header2)
          (ingest/delete-concept concept ingest-header3)
          (ingest/ingest-concept concept ingest-header4)
          (ingest/assert-user-id concept-id revision-id expected-user-id1)
          (ingest/assert-user-id concept-id (inc revision-id) expected-user-id2)
          (ingest/assert-user-id concept-id (inc (inc revision-id)) expected-user-id3)
          (ingest/assert-user-id concept-id (inc (inc (inc revision-id))) expected-user-id4))

        "user id from token"
        {:token (echo-util/login (system/context) "user1")} "user1"
        {:token (echo-util/login (system/context) "user2")} "user2"
        {:token (echo-util/login (system/context) "user3")} "user3"
        {:token nil} nil

        "user id from user-id header"
        {:user-id "user1"} "user1"
        {:user-id "user2"} "user2"
        {:user-id "user3"} "user3"
        {:user-id nil} nil))))

;; Subscription with concept-id ingest and update scenarios.
(deftest subscription-w-concept-id-ingest-test
  (let [supplied-concept-id "SUB1000-PROV1"
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        metadata {:concept-id supplied-concept-id
                  :CollectionConceptId (:concept-id coll1)
                  :native-id "Atlantic-1"}
        concept (subscription-util/make-subscription-concept metadata)]
    (testing "ingest of a new subscription concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))
    (testing "ingest of same native id and different providers is allowed"
      (let [concept2-id "SUB1000-PROV2"
            concept2 (subscription-util/make-subscription-concept
                      (assoc metadata :provider-id "PROV2"
                                      :concept-id concept2-id))
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept2)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [concept2-id 1] [concept-id revision-id]))))

    (testing "update the concept with the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (= [supplied-concept-id 2] [concept-id revision-id]))))

    (testing "update the concept without the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept
                                              (dissoc concept :concept-id))]
        (is (= [supplied-concept-id 3] [concept-id revision-id]))))))

(deftest update-subscription-notification
  (system/only-with-real-database
    (let [_ (dev-sys-util/freeze-time! "2020-01-01T10:00:00Z")
          coll1 (data-core/ingest-umm-spec-collection
                 "PROV1"
                 (data-umm-c/collection
                  {:ShortName "coll1"
                   :EntryTitle "entry-title1"})
                 {:token "mock-echo-system-token"})
          supplied-concept-id "SUB1000-PROV1"
          metadata {:concept-id supplied-concept-id
                    :CollectionConceptId (:concept-id coll1)
                    :native-id "Atlantic-1"}
          concept (subscription-util/make-subscription-concept metadata)]
      (subscription-util/ingest-subscription concept)
      (testing "send an update event to an new subscription"
        (let [resp (subscription-util/update-subscription-notification supplied-concept-id)]
          (is (= 204 (:status resp))))
        (let [resp (subscription-util/update-subscription-notification "-Fake-Id-")]
          (is (= 404 (:status resp))))
        (let [resp (subscription-util/update-subscription-notification "SUB8675309-foobar")]
          (is (= 404 (:status resp))))))))

(deftest subscription-ingest-schema-validation-test
  (testing "ingest of subscription concept JSON schema validation missing field"
    (let [concept (subscription-util/make-subscription-concept {:SubscriberId ""})
          {:keys [status errors]} (ingest/ingest-concept concept)]
      (is (= 400 status))
      (is (= ["#/SubscriberId: expected minLength: 1, actual: 0"]
             errors))))
  (testing "ingest of subscription concept JSON schema validation invalid field"
    (let [concept (subscription-util/make-subscription-concept {:InvalidField "xxx"})
          {:keys [status errors]} (ingest/ingest-concept concept)]
      (is (= 400 status))
      (is (= ["#: extraneous key [InvalidField] is not permitted"]
             errors)))))

(deftest subscription-update-error-test
  (let [supplied-concept-id "SUB1000-PROV1"
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        concept (subscription-util/make-subscription-concept
                 {:concept-id supplied-concept-id
                  :CollectionConceptId (:concept-id coll1)
                  :native-id "Atlantic-1"})
        _ (ingest/ingest-concept concept)]
    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (assoc concept :concept-id "SUB1111-PROV1"))]
        (is (= [409 [(str "A concept with concept-id [SUB1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:subscription] provider-id [PROV1]. "
                          "The given concept-id [SUB1111-PROV1] and native-id "
                          "[Atlantic-1] would conflict with that one.")]]
               [status errors]))))
    (testing "update concept with a different native-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (assoc concept :native-id "other"))]
        (is (= [409 [(str "A concept with concept-id [SUB1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:subscription] provider-id [PROV1]. "
                          "The given concept-id [SUB1000-PROV1] and native-id "
                          "[other] would conflict with that one.")]]
               [status errors]))))))

(deftest delete-subscription-ingest-test
  (testing "delete a subscription"
    (let [coll1 (data-core/ingest-umm-spec-collection
                 "PROV1"
                 (data-umm-c/collection
                  {:ShortName "coll1"
                   :EntryTitle "entry-title1"})
                 {:token "mock-echo-system-token"})
          concept (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll1)})
          _ (subscription-util/ingest-subscription concept)
          {:keys [status concept-id revision-id]}  (ingest/delete-concept concept)
          fetched (mdb/get-concept concept-id revision-id)]
      (is (= 200 status))
      (is (= 2 revision-id))
      (is (= (:native-id concept)
             (:native-id fetched)))
      (is (:deleted fetched))
      (testing "delete a deleted subscription"
        (let [{:keys [status errors]} (ingest/delete-concept concept)]
          (is (= [status errors]
                 [404 [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                               (:native-id concept) concept-id)]]))))
      (testing "create a subscription over a subscription's tombstone"
        (let [response (subscription-util/ingest-subscription
                        (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll1)}))
              {:keys [status concept-id revision-id]} response]
          (is (= 200 status))
          (is (= 3 revision-id)))))))

(deftest subscription-email-processing
  (testing "Tests subscriber-id filtering in subscription email processing job"
    (system/only-with-real-database
     (let [user1-group-id (echo-util/get-or-create-group (system/context) "group1")
           ;; User 1 is in group1
           user1-token (echo-util/login (system/context) "user1" [user1-group-id])
           _ (echo-util/ungrant (system/context)
                                (-> (access-control/search-for-acls (system/context)
                                                                    {:provider "PROV1"
                                                                     :identity-type "catalog_item"}
                                                                    {:token "mock-echo-system-token"})
                                    :items
                                    first
                                    :concept_id))
           _ (echo-util/ungrant (system/context)
                                (-> (access-control/search-for-acls (system/context)
                                                                    {:provider "PROV2"
                                                                     :identity-type "catalog_item"}
                                                                    {:token "mock-echo-system-token"})
                                    :items
                                    first
                                    :concept_id))
           _ (echo-util/grant (system/context)
                              [{:group_id user1-group-id
                                :permissions [:read]}]
                              :catalog_item_identity
                              {:provider_id "PROV1"
                               :name "Provider collection/granule ACL"
                               :collection_applicable true
                               :granule_applicable true
                               :granule_identifier {:access_value {:include_undefined_value true
                                                                   :min_value 1 :max_value 50}}})
           _ (echo-util/grant (system/context)
                              [{:user_type :registered
                                :permissions [:read]}]
                              :catalog_item_identity
                              {:provider_id "PROV2"
                               :name "Provider collection/granule ACL registered users"
                               :collection_applicable true
                               :granule_applicable true
                               :granule_identifier {:access_value {:include_undefined_value true
                                                                   :min_value 100 :max_value 200}}})
           _ (ac-util/wait-until-indexed)
           ;; Setup collections
           coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll1"
                                                                               :EntryTitle "entry-title1"})
                                                       {:token "mock-echo-system-token"})
           coll2 (data-core/ingest-umm-spec-collection "PROV2"
                                                       (data-umm-c/collection {:ShortName "coll2"
                                                                               :EntryTitle "entry-title2"})
                                                       {:token "mock-echo-system-token"})
           _ (index/wait-until-indexed)
           ;; Setup subscriptions for each collection, for user1
           _ (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                     {:Name "test_sub_prov1"
                                                      :SubscriberId "user1"
                                                      :EmailAddress "user1@nasa.gov"
                                                      :CollectionConceptId (:concept-id coll1)
                                                      :Query " "})
                                                    {:token "mock-echo-system-token"})
           _ (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                     {:provider-id "PROV2"
                                                      :Name "test_sub_prov1"
                                                      :SubscriberId "user1"
                                                      :EmailAddress "user1@nasa.gov"
                                                      :CollectionConceptId (:concept-id coll2)
                                                      :Query " "})
                                                    {:token "mock-echo-system-token"})
           _ (index/wait-until-indexed)
           ;; Setup granules, gran1 and gran3 with acl matched access-value
           ;; gran 2 does not match, and should not be readable by user1
           gran1 (data-core/ingest "PROV1"
                                   (data-granule/granule-with-umm-spec-collection coll1
                                                                                  (:concept-id coll1)
                                                                                  {:granule-ur "Granule1"
                                                                                   :access-value 33})
                                   {:token "mock-echo-system-token"})
           gran2 (data-core/ingest "PROV1"
                                   (data-granule/granule-with-umm-spec-collection coll1
                                                                                  (:concept-id coll1)
                                                                                  {:granule-ur "Granule2"
                                                                                   :access-value 66})
                                   {:token "mock-echo-system-token"})
           gran3 (data-core/ingest "PROV2"
                                   (data-granule/granule-with-umm-spec-collection coll2
                                                                                  (:concept-id coll2)
                                                                                  {:granule-ur "Granule3"
                                                                                   :access-value 133})
                                   {:token "mock-echo-system-token"})
           _ (index/wait-until-indexed)
           expected (set [(:concept-id gran1) (:concept-id gran3)])
           actual (->> (process-subscriptions)
                       (map #(nth % 1))
                       flatten
                       (map :concept-id)
                       set)]
       (is (= expected actual))))))

(deftest roll-your-own-subscription-tests
  ;; Use cases coming from EarthData Search wanting to allow their users to create
  ;; subscriptions without the need to have any acls
  (let [acls (ac-util/search-for-acls (transmit-config/echo-system-token)
                                      {:identity-type "provider"
                                       :provider "PROV1"
                                       :target "INGEST_MANAGEMENT_ACL"})
        _ (echo-util/ungrant (system/context) (:concept_id (first (:items acls))))
        _ (ac-util/wait-until-indexed)
        supplied-concept-id "SUB1000-PROV1"
        user1-token (echo-util/login (system/context) "user1")
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        concept (subscription-util/make-subscription-concept {:concept-id supplied-concept-id
                                                              :CollectionConceptId (:concept-id coll1)
                                                              :SubscriberId "user1"
                                                              :native-id "Atlantic-1"})]
    ;; caes 1 test against guest token - no subscription
    (testing "guest token - guests can not create subscriptions - passes"
      (let [{:keys [status errors]} (ingest/ingest-concept concept)]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))
    ;; case 2 test on system token (ECHO token) - should pass
    (testing "system token - admins can create subscriptions - passes"
      (let [{:keys [status errors]} (ingest/ingest-concept concept {:token (transmit-config/echo-system-token)})]
        (is (or (= 200 status) (= 201 status)))
        (is (nil? errors))))
    ;; case 3 test account 1 which matches metadata data user - should pass
    (testing "use an account which matches the metadata and does not have ACL - passes"
      (let [{:keys [status errors]} (ingest/ingest-concept concept {:token user1-token})]
        (is (= 200 status))
        (is (nil? errors))))
    ;; case 4 test account 3 which does NOT match metadata user and account does not have prems
    (testing "use an account which does not matches the metadata and does not have ACL - fails"
      (let [user3-token (echo-util/login (system/context) "user3")
            {:keys [status errors]} (ingest/ingest-concept concept {:token user3-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))))

;; case 5 test account 2 which does NOT match metadata user and account has prems ; this should be MMT's use case
(deftest roll-your-own-subscription-and-have-acls-tests-with-acls
  (testing "Use an account which does not match matches the metadata and DOES have an ACL"
    (let [user2-token (echo-util/login (system/context) "user2")
          supplied-concept-id "SUB1000-PROV1"
          coll1 (data-core/ingest-umm-spec-collection
                 "PROV1"
                 (data-umm-c/collection
                  {:ShortName "coll1"
                   :EntryTitle "entry-title1"})
                 {:token "mock-echo-system-token"})
          concept (subscription-util/make-subscription-concept {:concept-id supplied-concept-id
                                                                :SubscriberId "user1"
                                                                :CollectionConceptId (:concept-id coll1)
                                                                :native-id "Atlantic-1"})
          {:keys [status errors]} (ingest/ingest-concept concept {:token user2-token})]
      (is (= 201 status))
      (is (nil? errors)))))

(deftest update-and-delete-subscription-as-subscriber-via-put
  (testing "Tests updating and deleting subscriptions as subscriber"
    (echo-util/ungrant-by-search (system/context)
                                 {:provider "PROV1"
                                  :target ["SUBSCRIPTION_MANAGEMENT"]})
    (ac-util/wait-until-indexed)
    (let [admin-group (echo-util/get-or-create-group (system/context) "admin-group")
          admin-user-token (echo-util/login (system/context) "admin-user" [admin-group])]
      (echo-util/grant-all-subscription-group-sm (system/context)
                                                 "PROV1"
                                                 admin-group
                                                 [:read :update])
      (echo-util/grant-groups-ingest (system/context)
                                     "PROV1"
                                     [admin-group])
      (ac-util/wait-until-indexed)
      (let [user1-token (echo-util/login (system/context) "user1")
            user2-token (echo-util/login (system/context) "user2")
            coll1 (data-core/ingest-umm-spec-collection
                   "PROV1"
                   (data-umm-c/collection
                    {:ShortName "coll1"
                     :EntryTitle "entry-title1"})
                   {:token "mock-echo-system-token"})
            sub1-user1 (subscription-util/make-subscription-concept
                        {:SubscriberId "user1"
                         :Name "sub-name1"
                         :native-id "sub1"
                         :CollectionConceptId (:concept-id coll1)})
            sub1-user2 (subscription-util/make-subscription-concept
                        {:SubscriberId "user2"
                         :Name "sub-name1"
                         :native-id "sub1"
                         :CollectionConceptId (:concept-id coll1)})
            sub2 (subscription-util/make-subscription-concept
                  {:SubscriberId "user1"
                   :Name "sub-name2"
                   :native-id "sub2"
                   :CollectionConceptId (:concept-id coll1)})
            {:keys [concept-id revision-id status]} (ingest/ingest-concept
                                                     sub1-user1 {:token user1-token})]

        ;; verify subscription with user1 as subscriber is created successfully
        (is (= 201 status))

        (are3 [ingest-api-call args expected-status expected-errors]
          (let [{:keys [status errors]} (apply ingest-api-call args)]
            (is (= expected-status status))
            (is (= expected-errors errors)))

          "Attempt to update subscription as user2"
          ingest/ingest-concept [sub1-user2 {:token user2-token}] 401 ["You do not have permission to perform that action."]

          "Attempt to delete subscription as user2"
          ingest/delete-concept [sub1-user2 {:token user2-token}] 401 ["You do not have permission to perform that action."]

          "Update subscription as user1"
          ingest/ingest-concept [sub1-user1 {:token user1-token}] 200 nil

          "Delete subscription as user1"
          ingest/delete-concept [sub1-user1 {:token user1-token}] 200 nil

          "Update subscription as user1 to change subscriber id to user2 is not allowed"
          ingest/ingest-concept [sub1-user2 {:token user1-token}] 401 ["You do not have permission to perform that action."]

          "Update subscription as admin-user to change subscriber id to user2 is allowed"
          ingest/ingest-concept [sub1-user2 {:token admin-user-token}] 200 nil

          "Ingest subscription as admin"
          ingest/ingest-concept [sub2 {:token admin-user-token}] 201 nil

          "Update subscription as admin"
          ingest/ingest-concept [sub2 {:token admin-user-token}] 200 nil

          "Delete subscription as admin"
          ingest/delete-concept [sub2 {:token admin-user-token}] 200 nil)))))

(deftest subscription-ingest-subscriber-collection-permission-check-test
  (testing "Tests that the subscriber has permission to view the collection before allowing ingest"
    (let [admin-group (echo-util/get-or-create-group (system/context) "admin-group")
          group1 (echo-util/get-or-create-group (system/context) "group1")
          admin-user-token (echo-util/login (system/context) "admin-user" [admin-group])
          user-token (echo-util/login (system/context) "user1" [group1])
          coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                      (data-umm-c/collection {:ShortName "coll1"
                                                                              :EntryTitle "entry-title1"})
                                                      {:token "mock-echo-system-token"})
          fake-concept (subscription-util/make-subscription-concept {:SubscriberId "user1"
                                                                     :CollectionConceptId "FAKE"})
          concept (subscription-util/make-subscription-concept {:SubscriberId "user1"
                                                                :CollectionConceptId (:concept-id coll1)})]
      ;; adjust permissions
      (echo-util/ungrant-by-search (system/context)
                                   {:provider "PROV1"
                                    :target ["SUBSCRIPTION_MANAGEMENT"]})
      (echo-util/ungrant-by-search (system/context)
                                   {:provider "PROV1"
                                    :identity-type "catalog_item"})
      (echo-util/grant-all-subscription-group-sm (system/context)
                                                 "PROV1"
                                                 admin-group
                                                 [:read :update])
      (echo-util/grant-groups-ingest (system/context)
                                     "PROV1"
                                     [admin-group])
      (ac-util/wait-until-indexed)

      (testing "non-existent collection concept-id"
        (let [{:keys [status errors]} (ingest/ingest-concept fake-concept {:token "mock-echo-system-token"})]
          (is (= 401 status))
          (is (= [(format "Collection with concept id [FAKE] does not exist or subscriber-id [user1] does not have permission to view the collection.")]
                 errors))))

      (testing "user doesn't have permission to view collection"
        (are3 [ingest-api-call args expected-status expected-errors]
              (let [{:keys [status errors]} (apply ingest-api-call args)]
                (is (= expected-status status))
                (is (= expected-errors errors)))

              "Attempt to ingest subscription as user1"
              ingest/ingest-concept [concept {:token user-token}] 401 [(format "Collection with concept id [%s] does not exist or subscriber-id [user1] does not have permission to view the collection." (:concept-id coll1))]

              "Attempt to ingest subscription as admin-user"
              ingest/ingest-concept [concept {:token admin-user-token}] 401 [(format "Collection with concept id [%s] does not exist or subscriber-id [user1] does not have permission to view the collection." (:concept-id coll1))]))

      (echo-util/ungrant-by-search (system/context)
                                   {:provider "PROV1"
                                    :identity-type "catalog_item"})
      (ac-util/create-acl "mock-echo-system-token"
                          {:group_permissions [{:group_id group1
                                                :permissions ["read" "order"]}]
                           :catalog_item_identity {:name "coll1 ACL"
                                                   :provider_id "PROV1"
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles [(:EntryTitle coll1)]}}})
      (ac-util/wait-until-indexed)
      (testing "user now has permission to view collection - by group-id"
        (are3 [ingest-api-call args expected-status expected-errors]
              (let [{:keys [status errors]} (apply ingest-api-call args)]
                (is (= expected-status status))
                (is (= expected-errors errors)))

              "Ingest subscription as admin"
              ingest/ingest-concept [concept {:token admin-user-token}] 201 nil

              "Update subscription as admin"
              ingest/ingest-concept [concept {:token admin-user-token}] 200 nil

              "Delete subscription as admin"
              ingest/delete-concept [concept {:token admin-user-token}] 200 nil

              "Ingest subscription as user1"
              ingest/ingest-concept [concept {:token user-token}] 200 nil

              "Update subscription as user1"
              ingest/ingest-concept [concept {:token user-token}] 200 nil

              "Delete subscription as user1"
              ingest/delete-concept [concept {:token user-token}] 200 nil))

      (ac-util/create-acl "mock-echo-system-token"
                          {:group_permissions [{:user_type "registered"
                                                :permissions ["read" "order"]}]
                           :catalog_item_identity {:name "coll1 ACL"
                                                   :provider_id "PROV1"
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles [(:EntryTitle coll1)]}}})
      (ac-util/wait-until-indexed)
      (testing "user now has permission to view collection - by registered"
        (are3 [ingest-api-call args expected-status expected-errors]
              (let [{:keys [status errors]} (apply ingest-api-call args)]
                (is (= expected-status status))
                (is (= expected-errors errors)))

              "Ingest subscription as admin"
              ingest/ingest-concept [concept {:token admin-user-token}] 200 nil

              "Update subscription as admin"
              ingest/ingest-concept [concept {:token admin-user-token}] 200 nil

              "Delete subscription as admin"
              ingest/delete-concept [concept {:token admin-user-token}] 200 nil

              "Ingest subscription as user1"
              ingest/ingest-concept [concept {:token user-token}] 200 nil

              "Update subscription as user1"
              ingest/ingest-concept [concept {:token user-token}] 200 nil

              "Delete subscription as user1"
              ingest/delete-concept [concept {:token user-token}] 200 nil)))))

(deftest create-subscription-by-post
  (let [token (echo-util/login (system/context) "post-user")
        coll (data-core/ingest-umm-spec-collection
              "PROV1"
              (data-umm-c/collection)
              {:token "mock-echo-system-token"})]

    (testing "without native-id provided"
      (let [concept (dissoc (subscription-util/make-subscription-concept
                             {:SubscriberId "post-user"
                              :Name "no native-id"
                              :CollectionConceptId (:concept-id coll)})
                            :native-id)
            {:keys [native-id concept-id status]} (ingest/ingest-concept
                                                   concept
                                                   {:token token
                                                    :method :post})]
        (is (= 201 status))
        (is (not (nil? concept-id)))
        (is (not (nil? native-id)))
        (is (string/starts-with? native-id "no_native_id"))

        (index/wait-until-indexed)

        (is (not (nil? (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)})))))))))

    (testing "with native-id provided"
      (let [concept (subscription-util/make-subscription-concept
                     {:SubscriberId "post-user"
                      :Name "a different subscription with native-id"
                      :native-id "another-native-id"
                      :CollectionConceptId (:concept-id coll)})
            {:keys [native-id concept-id status]} (ingest/ingest-concept concept {:token token
                                                                                  :method :post})]
        (is (= 201 status))
        (is (not (nil? concept-id)))
        (is (= "another-native-id" native-id))

        (index/wait-until-indexed)

        (is (= (:native-id concept)
               (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)}))))))

        (testing "update subscription with POST is not allowed"
          (let [{:keys [status errors]} (ingest/ingest-concept concept {:token token
                                                                        :method :post})]
            (is (= 409 status))
            (is (= ["Subscription with with provider-id [PROV1] and native-id [another-native-id] already exists."]
                   errors))))))

    (testing "without native-id provided with unicode in the name"
      (let [concept (dissoc (subscription-util/make-subscription-concept
                             {:SubscriberId "post-user"
                              :Name "unicode-test Großartiger Scott!"
                              :CollectionConceptId (:concept-id coll)})
                            :native-id)
            {:keys [status concept-id native-id revision-id]}
            (ingest/ingest-concept concept
                                   {:token token
                                    :method :post})]
        (is (= 201 status))
        (is (not (nil? concept-id)))
        (is (string/starts-with? native-id "unicode_test_großartiger"))
        (is (= 1 revision-id))

        (index/wait-until-indexed)

        (is (not (nil? (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)})))))))))))

(deftest create-subscription-by-put
  (let [token (echo-util/login (system/context) "put-user")
        coll (data-core/ingest-umm-spec-collection
              "PROV1"
              (data-umm-c/collection)
              {:token "mock-echo-system-token"})]

    (testing "without native-id returns an error"
      (let [concept (dissoc (subscription-util/make-subscription-concept
                             {:SubscriberId "post-user"
                              :Name "no native-id"
                              :CollectionConceptId (:concept-id coll)})
                            :native-id)
            {:keys [status]} (ingest/ingest-concept concept
                                                    {:token token
                                                     :method :put
                                                     :raw? true})]
        ;; There is no PUT handler for subscriptions without a native-id
        (is (= 404 status))))

    (testing "subscription creation using PUT"
      (let [concept (assoc (subscription-util/make-subscription-concept
                            {:SubscriberId "post-user"
                             :Name "a different subscription with native-id"
                             :CollectionConceptId (:concept-id coll)})
                           :native-id "another-native-id")
            {:keys [native-id concept-id status]} (ingest/ingest-concept concept {:token token
                                                                                  :method :put})]
        (is (= 201 status))
        (is (not (nil? concept-id)))
        (is (= "another-native-id" native-id))

        (index/wait-until-indexed)

        (is (= (:native-id concept)
               (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)}))))))))

    (testing "subscription update using PUT"
      (let [concept (assoc (subscription-util/make-subscription-concept
                            {:SubscriberId "post-user"
                             :Name "a different subscription with native-id"
                             :CollectionConceptId (:concept-id coll)})
                           :native-id "another-native-id")
            {:keys [status revision-id]} (ingest/ingest-concept concept {:token token})]
        (is (= 200 status))
        (is (= 2 revision-id))

        (index/wait-until-indexed)

        (is (= (:native-id concept)
               (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)}))))))))))
