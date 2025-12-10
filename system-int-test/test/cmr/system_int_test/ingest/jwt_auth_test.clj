(ns cmr.system-int-test.ingest.jwt-auth-test
  "Integration tests for JWT authentication across collections, granules, and bulk operations"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common-app.test.side-api :as side]
   [cmr.common-app.config :as config]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.transmit.tokens :as tokens]))

;; Mock JWT tokens
(def mock-jwt-level-5 "MOCK-JWT-LEVEL-5")
(def mock-jwt-level-4-user1 "MOCK-JWT-LEVEL-4-USER1")
(def mock-jwt-level-4-user2 "MOCK-JWT-LEVEL-4-USER2")
(def mock-jwt-level-3 "MOCK-JWT-LEVEL-3")

;; Mock get-jwt-claims to return controlled values
(defn mock-get-jwt-claims [token]
  (case token
    "MOCK-JWT-LEVEL-5" {:uid "jwt-user-l5" :assurance_level 5}
    "MOCK-JWT-LEVEL-4-USER1" {:uid "jwt-user-l4-1" :assurance_level 4}
    "MOCK-JWT-LEVEL-4-USER2" {:uid "jwt-user-l4-2" :assurance_level 4}
    "MOCK-JWT-LEVEL-3" {:uid "jwt-user-l3" :assurance_level 3}
    nil))

(use-fixtures :each
  (fn [f]
    (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                  util/is-jwt-token? (fn [token] (re-find #"^MOCK-JWT-" token))]
      (ingest/reset-fixture {"provguid1" "PROV1"})
      ;; Enable JWT authentication for all tests
      (side/eval-form `(config/set-enable-idfed-jwt-authentication! true))
      (side/eval-form `(config/set-launchpad-token-enforced! true))
      (side/eval-form `(config/set-required-assurance-level! 4))
      (f))))

;; =============================================================================
;; Collection Tests
;; =============================================================================

(deftest collection-ingest-with-level-5-jwt-test
  (testing "Level 5 JWT - Full access without NON_NASA_DRAFT_USER ACL"
    (echo-util/grant-group-ingest-management
     (system/context)
     [{:user-id "jwt-user-l5"}]
     :update
     "PROV1")

    (let [concept (data-umm-c/collection-concept {:provider-id "PROV1"})
          {:keys [status concept-id errors]} (ingest/ingest-concept
                                              concept
                                              {:token mock-jwt-level-5
                                               :validate-keywords false})]
      (is (= 201 status) (str "Errors: " errors))
      (is concept-id))))

(deftest collection-ingest-with-level-4-jwt-with-acl-test
  (testing "Level 4 JWT - Success with NON_NASA_DRAFT_USER ACL"
    (echo-util/grant-group-ingest-management
     (system/context)
     [{:user-id "jwt-user-l4-1"}]
     :update
     "PROV1")

    (echo-util/grant-group-non-nasa-draft-user
     (system/context)
     [{:user-id "jwt-user-l4-1"}]
     :update
     "PROV1")

    (let [concept (data-umm-c/collection-concept {:provider-id "PROV1"})
          {:keys [status concept-id errors]} (ingest/ingest-concept
                                              concept
                                              {:token mock-jwt-level-4-user1
                                               :validate-keywords false})]
      (is (= 201 status) (str "Errors: " errors))
      (is concept-id))))

(deftest collection-ingest-with-level-4-jwt-without-acl-test
  (testing "Level 4 JWT - Failure without NON_NASA_DRAFT_USER ACL"
    (echo-util/grant-group-ingest-management
     (system/context)
     [{:user-id "jwt-user-l4-2"}]
     :update
     "PROV1")

    (let [concept (data-umm-c/collection-concept {:provider-id "PROV1"})
          {:keys [status errors]} (ingest/ingest-concept
                                   concept
                                   {:token mock-jwt-level-4-user2
                                    :validate-keywords false})]
      (is (= 401 status))
      (is (some #(re-find #"permission" %) errors)))))

(deftest collection-ingest-with-level-3-jwt-test
  (testing "Level 3 JWT - Rejected (insufficient assurance level)"
    ;; Even with full permissions, level 3 should be rejected
    (echo-util/grant-group-ingest-management
     (system/context)
     [{:user-id "jwt-user-l3"}]
     :update
     "PROV1")

    (let [concept (data-umm-c/collection-concept {:provider-id "PROV1"})
          {:keys [status errors]} (ingest/ingest-concept
                                   concept
                                   {:token mock-jwt-level-3
                                    :validate-keywords false})]
      (is (= 401 status))
      (is (some #(re-find #"permission" %) errors)))))

;; =============================================================================
;; Granule Tests
;; =============================================================================

(deftest granule-ingest-with-level-5-jwt-test
  (testing "Granule ingest with level 5 JWT"
    (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))]

      (echo-util/grant-group-ingest-management
       (system/context)
       [{:user-id "jwt-user-l5"}]
       :update
       "PROV1")

      (let [granule (dg/granule-with-umm-spec-collection coll "C1-PROV1")
            {:keys [status concept-id]} (d/ingest "PROV1"
                                                  granule
                                                  {:token mock-jwt-level-5})]
        (is (= 201 status))
        (is concept-id)))))

(deftest granule-delete-with-level-4-jwt-test
  (testing "Granule delete with level 4 JWT + ACL"
    (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (dg/granule-with-umm-spec-collection coll "C1-PROV1")
          {:keys [concept-id]} (d/ingest "PROV1" granule)]

      (echo-util/grant-group-ingest-management
       (system/context)
       [{:user-id "jwt-user-l4-1"}]
       :update
       "PROV1")
      (echo-util/grant-group-non-nasa-draft-user
       (system/context)
       [{:user-id "jwt-user-l4-1"}]
       :update
       "PROV1")

      (let [{:keys [status]} (ingest/delete-concept
                              concept-id
                              {:token mock-jwt-level-4-user1})]
        (is (= 200 status))))))
