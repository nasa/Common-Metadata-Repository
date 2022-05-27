(ns cmr.system-int-test.ingest.subscription-ingest-test
  "CMR subscription ingest integration tests.
  For subscription permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac-util]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.util :refer [are3]]
   [cmr.ingest.services.subscriptions-helper :as jobsub]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.subscription-util :as subscription-util]
   [cmr.system-int-test.utils.tag-util :as tags]
   [cmr.transmit.config :as transmit-config]))

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
                                                                  [:read :update])
                tags/grant-all-tag-fixture]))

(deftest subscription-count-exceeds-limit-test
  (side/eval-form `(jobsub/set-subscriptions-limit! 1))
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll-exceed-limit-1"
                 :EntryTitle "entry-title-exceed-limit-1"})
               {:token "mock-echo-system-token"})
        user1-token (echo-util/login (system/context) "user1")]
    (testing "Succeeds when ingesting subscription for user below their subscription limit"
      (let [concept (subscription-util/make-subscription-concept {:Query "platform=NOAA-7"
                                                                  :Name "sub1"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId "user1"})
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 201 (:status response)))
        (is (mdb/concept-exists-in-mdb? (:concept-id response) (:revision-id response)))))
    (testing "Validating that subscription revisions are not included when calculating subscription count"
      (let [concept (subscription-util/make-subscription-concept {:Query "platform=NOAA-5"
                                                                  :Name "sub1"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId "user1"})
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 200 (:status response)))
        (is (mdb/concept-exists-in-mdb? (:concept-id response) (:revision-id response)))))
    (testing "Fails with correct response when ingesting subscription for user beyond their subscription limit"
      (let [concept (subscription-util/make-subscription-concept {:Query "platform=NOAA-9"
                                                                  :Name "sub2"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId "user1"})
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 409 (:status response)))
        (is (= "The subscriber-id [user1] has already reached the subscription limit." (first (:errors response))))))
    (testing "Deleting a subscription to validate deleted subscriptions do not count towards subscription limit"
      (let [concept (subscription-util/make-subscription-concept {:Query "platform=NOAA-9"
                                                                  :Name "sub1"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId "user1"})
            user1-token (echo-util/login (system/context) "user1")
            response (ingest/delete-concept concept {:token user1-token})]
        (is (= 200 (:status response))))
      (let [concept (subscription-util/make-subscription-concept {:Query "platform=NOAA-4"
                                                                  :Name "sub3"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId "user1"})
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 201 (:status response)))
        (is (mdb/concept-exists-in-mdb? (:concept-id response) (:revision-id response)))))
    (side/eval-form `(jobsub/set-subscriptions-limit! 100))
    (testing "Succeeds when ingesting subscription for user below the updated subscription limit"
      (let [concept (subscription-util/make-subscription-concept {:Query "platform=NOAA-9"
                                                                  :Name "sub2"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId "user1"})
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 201 (:status response)))
        (is (mdb/concept-exists-in-mdb? (:concept-id response) (:revision-id response)))))))

(deftest subscription-ingest-no-subscriber-id-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll-no-id"
                 :EntryTitle "entry-title-no-id"})
               {:token "mock-echo-system-token"})]
    (mock-urs/create-users (system/context) [{:username "user1" :password "Password"}])
    (testing "ingest on PROV1, with no subscriber-id supplied"
      (let [concept (subscription-util/make-subscription-concept {:provider-id "PROV3"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId nil
                                                                  :EmailAddress "foo@example.com"})
            user1-token (echo-util/login (system/context) "user1")
            response (ingest/ingest-concept concept {:token user1-token})
            ingested-concept (mdb/get-concept (:concept-id response))
            parsed-metadata (json/parse-string (:metadata ingested-concept) true)]
        (is (= 201 (:status response)))
        (is (= "user1" (:SubscriberId parsed-metadata)))))
    (testing "ingest on PROV1, with no subscriber-id supplied and no token used to ingest"
      (let [concept (subscription-util/make-subscription-concept {:provider-id "PROV3"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId nil
                                                                  :EmailAddress "foo@example.com"})
            response (ingest/ingest-concept concept)]
        (is (= 400 (:status response)))
        (is (= "Subscription creation failed - No ID was provided. Please provide a SubscriberId or pass in a valid token." (first (:errors response))))))))

(deftest subscription-ingest-on-prov3-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV3"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        concept (subscription-util/make-subscription-concept
                 {:CollectionConceptId (:concept-id coll1)})
        guest-token (echo-util/login-guest (system/context))]

    (testing "ingest on PROV3, guest is not granted ingest permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [{:keys [status errors]} (ingest/ingest-concept concept {:token guest-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "ingest on PROV3, registered user is granted ingest permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [user1-token (echo-util/login (system/context) "user1")
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 201 (:status response)))))))

(deftest subscription-delete-on-prov3-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV3"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        concept (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll1)
                                                              :provider-id "PROV3"})
        user1-token (echo-util/login (system/context) "user1")]
    (ingest/ingest-concept concept {:token user1-token})
    (index/wait-until-indexed)

    (testing "delete on PROV3, guest is not granted update permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [guest-token (echo-util/login-guest (system/context))
            {:keys [status errors]} (ingest/delete-concept concept {:token guest-token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."]
               errors))))

    (testing "delete on PROV3, registered user is granted update permission for SUBSCRIPTION_MANAGEMENT ACL"
      (let [{:keys [status errors]} (ingest/delete-concept concept {:token user1-token})]
        (is (= 200 status))))))

(deftest umm-sub-1_0-subscription-ingest-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
    (testing "ingest of a new subscription concept"
      (let [concept (subscription-util/make-subscription-concept-with-umm-version
                     "1.0" {:CollectionConceptId (:concept-id coll1)})
            {:keys [concept-id revision-id status]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))))))

(deftest subscription-ingest-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
    (testing "ingest of a new granule subscription concept"
      (let [concept (subscription-util/make-subscription-concept
                     {:CollectionConceptId (:concept-id coll1)})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))))
    (testing "ingest of a granule subscription concept with a revision id"
      (let [concept (subscription-util/make-subscription-concept
                     {:CollectionConceptId (:concept-id coll1)}
                     {:revision-id 5})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (= 5 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id 5))))
    (testing "ingest of a new collection subscription concept"
      (let [concept (subscription-util/make-subscription-concept
                     {:Type "collection"}
                     {}
                     "coll-sub")
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))))
    (testing "ingest of a collection subscription concept with a revision id"
      (let [concept (subscription-util/make-subscription-concept
                     {:Type "collection"}
                     {:revision-id 5}
                     "coll-sub")
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
    (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
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
               (dissoc (ingest/parse-ingest-body :json response) :body)))))

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
               (dissoc (ingest/parse-ingest-body :xml response) :body)))))))

(deftest subscription-ingest-with-bad-query-error-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (testing "ingest of a new granule subscription concept with invalid query"
    (let [coll1 (data-core/ingest-umm-spec-collection
                 "PROV1"
                 (data-umm-c/collection
                  {:ShortName "coll1"
                   :EntryTitle "entry-title1"})
                 {:token "mock-echo-system-token"})
          metadata {:CollectionConceptId (:concept-id coll1)
                    :Query "options%5Bspatial%5D%5Bor%5D=true&platform=MODIS"}
          concept (subscription-util/make-subscription-concept metadata)
          response (ingest/ingest-concept
                    concept
                    {:accept-format :json
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)
          error (first errors)]
      (is (re-find #"Subscription query validation failed with the following error" error))
      (is (re-find #"Parameter \[options%_5_bspatial%_5_d%_5_bor%_5_d\] was not recognized." error))))
  (testing "ingest of a new collection subscription concept with invalid query"
    (let [metadata {:Query "producer_granule_id[]=DummyID"
                    :Type "collection"}
          concept (subscription-util/make-subscription-concept metadata)
          response (ingest/ingest-concept
                    concept
                    {:accept-format :json
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)
          error (first errors)]
      (is (re-find #"Subscription query validation failed with the following error" error))
      (is (re-find #"Parameter \[producer_granule_id\] was not recognized." error)))))

(deftest subcription-ingest-collection-subscription-duplicate-error-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (testing "ingest of a new collection subscription concept with invalid query"
    (let [metadata {:Query " "
                    :Type "collection"}
          concept1 (subscription-util/make-subscription-concept metadata)
          concept2 (subscription-util/make-subscription-concept (assoc  metadata :Name "Duplicate"))
          _ (ingest/ingest-concept
             concept1
             {:accept-format :json
              :raw? true})
          response (ingest/ingest-concept
                    concept2
                    {:accept-format :json
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)
          error (first errors)]
      (is (re-find #"The subscriber-id \[someSubId\] has already subscribed using the query \[.*\]. Subscribers must use unique queries for each collection subscription" error)))))

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
      (is (= ["Request content is too short."] errors))))
  (testing "xml response"
    (let [concept-no-metadata (assoc (subscription-util/make-subscription-concept)
                                     :metadata "")
          response (ingest/ingest-concept
                    concept-no-metadata
                    {:accept-format :xml
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (= ["Request content is too short."] errors)))))

;; Verify that user-id is saved from User-Id or token header
(deftest subscription-ingest-user-id-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})]
    (mock-urs/create-users (system/context) [{:username "user1" :password "Pass1"}
                                             {:username "user2" :password "Pass2"}
                                             {:username "user3" :password "Pass3"}
                                             {:username "user4" :password "Pass4"}
                                             {:username "someSubId" :password "somePass"}])
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
        native-id "Atlantic-1"
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll1"
                 :EntryTitle "entry-title1"})
               {:token "mock-echo-system-token"})
        metadata {:concept-id supplied-concept-id
                  :CollectionConceptId (:concept-id coll1)
                  :native-id native-id}
        concept (subscription-util/make-subscription-concept metadata)]
    (mock-urs/create-users (system/context) [{:username "someSubId" :password "Pass"}])
    (testing "ingest of a new subscription concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))
    (testing "ingest of same native id on different provider is not allowed"
      (let [coll2 (data-core/ingest-umm-spec-collection
                   "PROV2"
                   (data-umm-c/collection
                    {:ShortName "coll2"
                     :EntryTitle "entry-title2"})
                   {:token "mock-echo-system-token"})
            concept2-id "SUB1000-PROV2"
            concept2 (subscription-util/make-subscription-concept
                      {:concept-id concept2-id
                       :CollectionConceptId (:concept-id coll2)
                       :native-id native-id})
            {:keys [status errors]} (ingest/ingest-concept (assoc concept2 :provider-id "PROV2"))]
        (is (= 409 status))
        (is (= [(format
                 (str "A concept with concept-id [%s] and native-id [%s] already exists "
                      "for concept-type [:subscription] provider-id [PROV2]. "
                      "The given concept-id [%s] and native-id [%s] would conflict with that one.")
                 supplied-concept-id
                 native-id
                 concept2-id
                 native-id)]
               errors))))

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
     (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
     (subscription-util/ingest-subscription concept)
     (testing "send an update event to an new subscription"
       (let [resp (subscription-util/update-subscription-notification supplied-concept-id)]
         (is (= 204 (:status resp))))
       (let [resp (subscription-util/update-subscription-notification "-Fake-Id-")]
         (is (= 404 (:status resp))))
       (let [resp (subscription-util/update-subscription-notification "SUB8675309-foobar")]
         (is (= 404 (:status resp))))))))

(deftest subscription-ingest-schema-validation-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
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
             errors))))

  (testing "ingest of subscription concept with invalid JSON"
    (let [sub-metadata (slurp (io/resource "CMR-8226/subscription_invalid_json.json"))
          {:keys [status errors]} (ingest/ingest-concept
                            (ingest/concept :subscription "PROV1" "foo" :umm-json sub-metadata))]
      (is (= 400 status))
      (is (= ["Invalid JSON: Expected a ',' or '}' at 151 [character 3 line 6]"] errors))))

  (testing "ingest of granule subscription concept without the CollectionConceptId field"
    (let [sub-metadata (slurp (io/resource "CMR-8226/granule_subscription_wo_coll_concept_id.json"))
          {:keys [status errors]} (ingest/ingest-concept
                            (ingest/concept :subscription "PROV1" "foo" :umm-json sub-metadata))]
      (is (= 400 status))
      (is (= ["Granule subscription must specify CollectionConceptId."] errors))))

  (testing "ingest of collection subscription concept with the CollectionConceptId field"
    (let [sub-metadata (slurp (io/resource "CMR-8226/coll_subscription_w_coll_concept_id.json"))
          {:keys [status errors]} (ingest/ingest-concept
                            (ingest/concept :subscription "PROV1" "foo" :umm-json sub-metadata))]
      (is (= 400 status))
      (is (= ["Collection subscription cannot specify CollectionConceptId."]
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
                  :Query "platform=NOAA-7"
                  :native-id "Atlantic-1"})
        concept2 (subscription-util/make-subscription-concept
                  {:concept-id supplied-concept-id
                   :CollectionConceptId (:concept-id coll1)
                   :Query "platform=NOAA-9"
                   :native-id "other"})
        _ (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
        _ (ingest/ingest-concept concept)]
    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (assoc concept :Query "platform=NOAA-10" :concept-id "SUB1111-PROV1"))]
        (is (= [409 [(str "A concept with concept-id [SUB1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:subscription] provider-id [PROV1]. "
                          "The given concept-id [SUB1111-PROV1] and native-id "
                          "[Atlantic-1] would conflict with that one.")]]
               [status errors]))))
    (testing "update concept with a different native-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept concept2)]
        (is (= [409 [(str "A concept with concept-id [SUB1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:subscription] provider-id [PROV1]. "
                          "The given concept-id [SUB1000-PROV1] and native-id "
                          "[other] would conflict with that one.")]]
               [status errors]))))))

(deftest delete-subscription-ingest-test
  (mock-urs/create-users (system/context) [{:username "someSubId" :password "Password"}])
  (testing "delete a subscription"
    (let [coll1 (data-core/ingest-umm-spec-collection
                 "PROV1"
                 (data-umm-c/collection
                  {:ShortName "coll1"
                   :EntryTitle "entry-title1"})
                 {:token "mock-echo-system-token"})
          concept (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll1)})
          user1-token (echo-util/login (system/context) "user1")
          _ (subscription-util/ingest-subscription concept {:token user1-token})
          {:keys [status concept-id revision-id]}  (ingest/delete-concept concept {:token user1-token})
          fetched (mdb/get-concept concept-id revision-id)]
      (is (= 200 status))
      (is (= 2 revision-id))
      (is (= (:native-id concept)
             (:native-id fetched)))
      (is (:deleted fetched))
      (testing "delete a deleted subscription"
        (let [{:keys [status errors]} (ingest/delete-concept concept {:token user1-token})]
          (is (= [404 [(format "Subscription with native-id [%s] has already been deleted."
                               (:native-id concept))]]
                 [status errors]))))
      (testing "create a subscription over a subscription's tombstone"
        (let [response (subscription-util/ingest-subscription
                        (subscription-util/make-subscription-concept {:CollectionConceptId (:concept-id coll1)}))
              {:keys [status concept-id revision-id]} response]
          (is (= 200 status))
          (is (= 3 revision-id)))))))

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
  (mock-urs/create-users (system/context) [{:username "user1" :password "Password"}])
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
                                                                     :CollectionConceptId "C1234-PROV1"})
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
          (is (= [(format "Collection with concept id [C1234-PROV1] does not exist or subscriber-id [user1] does not have permission to view the collection.")]
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

    (testing "with blank native-id returns an error"
      (let [concept (assoc (subscription-util/make-subscription-concept
                            {:SubscriberId "post-user"
                             :Name "blank native-id"
                             :CollectionConceptId (:concept-id coll)})
                           :native-id " ")
            {:keys [status errors]} (ingest/ingest-concept concept
                                                           {:token token
                                                            :method :post})]
        (is (= 400 status))
        (is (= ["Subscription native-id provided is blank."] errors))))

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

        (is (some? (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)}))))))))

    (testing "with native-id provided"
      (let [input-native-id "another-native-id"
            concept (subscription-util/make-subscription-concept
                     {:SubscriberId "post-user"
                      :Name "a different subscription with native-id"
                      :native-id input-native-id
                      :Query "instrument=POSEIDON-2"
                      :CollectionConceptId (:concept-id coll)})
            {:keys [status native-id concept-id revision-id]} (ingest/ingest-concept
                                                               concept {:token token
                                                                        :method :post})
            expected-concept-id concept-id]
        (is (= 201 status))
        (is (not (nil? concept-id)))
        (is (= input-native-id native-id))
        (is (= 1 revision-id))

        (index/wait-until-indexed)

        (is (= (:native-id concept)
               (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)}))))))

        (testing "update subscription with POST is not allowed"
          (let [{:keys [status errors]} (ingest/ingest-concept concept {:token token
                                                                        :method :post})]
            (is (= 409 status))
            (is (= ["Subscription with native-id [another-native-id] already exists."]
                   errors))))
        (testing "once the existing subscription is deleted, POST with the same native-id is allowed"
          (ingest/delete-concept concept)
          (let [{:keys [status native-id concept-id revision-id]} (ingest/ingest-concept
                                                                   concept {:token token
                                                                            :method :post})]
            (is (= 200 status))
            (is (= input-native-id native-id))
            (is (= expected-concept-id concept-id))
            (is (= 3 revision-id))))))

    (testing "without native-id provided with unicode in the name"
      (let [concept (dissoc (subscription-util/make-subscription-concept
                             {:SubscriberId "post-user"
                              :Name "unicode-test Großartiger Scott!"
                              :Query "instrument=POSEIDON-2B"
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

        (is (some? (:native-id (first (:items (subscription-util/search-json {:name (:Name concept)}))))))))))

(deftest create-update-granule-subscription-by-put
  (let [token (echo-util/login (system/context) "put-user")
        coll (data-core/ingest-umm-spec-collection
              "PROV1"
              (data-umm-c/collection)
              {:token "mock-echo-system-token"})]
    (mock-urs/create-users (system/context) [{:username "post-user" :password "Password"}])
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

(deftest create-update-collection-subscription-by-put
  (let [token (echo-util/login (system/context) "put-user")
        sub-name "a collection subscription"
        input-native-id "my-native-id"
        coll-sub-concept (subscription-util/make-subscription-concept
                          {:Type "collection"
                           :SubscriberId "post-user"
                           :Name sub-name})]
    (mock-urs/create-users (system/context) [{:username "post-user" :password "Password"}])
    (testing "without native-id returns an error"
      (let [concept (dissoc coll-sub-concept :native-id)
            {:keys [status]} (ingest/ingest-concept concept
                                                    {:token token
                                                     :method :put
                                                     :raw? true})]
        ;; There is no PUT handler for subscriptions without a native-id
        (is (= 404 status))))

    (testing "collection subscription creation using PUT"
      (let [concept (assoc coll-sub-concept :native-id input-native-id)
            {:keys [status concept-id revision-id native-id]} (ingest/ingest-concept concept
                                                                                     {:token token
                                                                                      :method :put})]
        (is (= 201 status))
        (is (not (nil? concept-id)))
        (is (= 1 revision-id))
        (is (= input-native-id native-id))

        (index/wait-until-indexed)
        (let [found (first (:items (subscription-util/search-json {:name sub-name})))]
          (is (= native-id (:native-id found)))
          (is (= concept-id (:concept-id found))))

        (testing "collection subscription update using PUT"
          (let [new-sub-name "another subscription"
                new-concept (-> (subscription-util/make-subscription-concept
                                 {:Type "collection"
                                  :SubscriberId "post-user"
                                  :Name new-sub-name})
                                (assoc :native-id input-native-id))
                {update-status :status
                 update-concept-id :concept-id
                 update-revision-id :revision-id
                 update-native-id :native-id}
                (ingest/ingest-concept new-concept {:token token})]
            (is (= 200 update-status))
            (is (= concept-id update-concept-id))
            (is (= 2 update-revision-id))
            (is (= input-native-id update-native-id))

            (index/wait-until-indexed)
            (let [found (first (:items (subscription-util/search-json {:name new-sub-name})))]
              (is (= input-native-id (:native-id found)))
              (is (= concept-id (:concept-id found))))))))))

(deftest query-uniqueness-test
  (let [sub-user-group-id (echo-util/get-or-create-group (system/context) "sub-group")
        sub-user-token (echo-util/login (system/context) "sub-user" [sub-user-group-id])
        coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection {:ShortName "coll1"
                                                                            :EntryTitle "entry-title1"})
                                                    {:token "mock-echo-system-token"})
        coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection {:ShortName "coll2"
                                                                            :EntryTitle "entry-title2"})
                                                    {:token "mock-echo-system-token"})

        sub1 (subscription-util/create-subscription-and-index
              coll1 "test_sub1_prov1" "sub-user" "instrument=POSEIDON-2&platform=NOAA-7")
        ;;should fail, since normalized-query will be identical to sub1
        sub2 (subscription-util/create-subscription-and-index
              coll1 "test_sub2_prov1" "sub-user" "platform=NOAA-7&instrument=POSEIDON-2")
        ;;should succeed - query the same, but different collection
        sub3 (subscription-util/create-subscription-and-index
              coll2 "test_sub3_prov1" "sub-user" "platform=NOAA-7&instrument=POSEIDON-2")
        ;;Later on, we will delete this sub and supersede it
        sub4 (subscription-util/create-subscription-and-index
              coll2 "test_sub4_prov1" "sub-user" "platform=NOAA-11")
        sub4-concept {:provider-id "PROV1" :concept-type :subscription :native-id "test_sub4_prov1"}]

    (testing "check that only actual duplicate subscriptions failed"
      (is (:errors sub2))
      (is (not (:errors sub3))))
    (testing "check that ability to un-tombstone is not blocked by uniqueness rules"
      (let [sub3-concept {:provider-id "PROV1" :concept-type :subscription :native-id "test_sub3_prov1"}
            ;;delete sub3, and reingest it as-is.
            _ (ingest/delete-concept sub3-concept)
            sub3-2 (subscription-util/create-subscription-and-index
                    coll2 "test_sub3_prov1" "sub-user" "platform=NOAA-7&instrument=POSEIDON-2")]
        (is (not (:errors sub3)))))
    (testing "should be possible to replace a tombstoned concept with a new concept, with new native-id"
      (ingest/delete-concept sub4-concept)
      (let [;;We ingest sub5, which is identical to sub4 with a different native-id.
            ;;Since sub4 is deleted, we should be able to ingest this without error.
            sub5 (subscription-util/create-subscription-and-index
                  coll2 "test_sub5_prov1" "sub-user" "platform=NOAA-11")
            ;;attempt to un-tombstone should fail, since this unique combo has been taken sub5
            sub4-2 (subscription-util/create-subscription-and-index
                    coll2 "test_sub4_prov1" "sub-user" "platform=NOAA-11")]
        (is (not (:errors sub5)))
        (is (:errors sub4-2))))))

(deftest subscription-ingest-invalid-subscriber-id-test
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "coll-no-id"
                 :EntryTitle "entry-title-no-id"})
               {:token "mock-echo-system-token"})]
    (testing "ingest on PROV1, with no subscriber-id supplied"
      (let [concept (subscription-util/make-subscription-concept {:provider-id "PROV3"
                                                                  :CollectionConceptId (:concept-id coll1)
                                                                  :SubscriberId "invalid-user"
                                                                  :EmailAddress "foo@example.com"})
            user1-token (echo-util/login (system/context) "user1")
            response (ingest/ingest-concept concept {:token user1-token})]
        (is (= 400 (:status response)))
        (is (= "Subscription creation failed - The user-id [invalid-user] must correspond to a valid EDL account." (first (:errors response))))))))
