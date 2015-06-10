(ns cmr.metadata-db.int-test.provider-crud-test
  "Contains integration tests for deleting providers. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.common.util :as u]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-provider-test
  (testing "successful saves"
    (u/are2
      [provider-id cmr-only small]
      (let [{:keys [status]} (util/save-provider provider-id cmr-only small)]
        (and (= status 201)
             (util/verify-provider-was-saved provider-id
                                             (if cmr-only true false)
                                             (if small true false))))
      "cmr-only false small false" "PROV1" false false
      "cmr-only true small false" "PROV2" true false
      "cmr-only false small true" "PROV3" false true
      "cmr-only true small true" "PROV4" true true
      "cmr-only and small default to false" "PROV5" nil nil))
  (testing "save provider twice"
    (is (= 409 (:status (util/save-provider "PROV1" false false))))))

(deftest update-provider-test
  (testing "basic update"
    (util/save-provider "PROV1" false false)
    (is (util/verify-provider-was-saved "PROV1" false false))
    (util/update-provider "PROV1" true false)
    (is (util/verify-provider-was-saved "PROV1" true false)))
  (testing "cannot modify small field of a provider"
    (is (= 400 (:status (util/update-provider "PROV1" true true)))))
  (testing "update nonexistant provider"
    (is (= 404 (:status (util/update-provider "PROV2" true false)))))
  (testing "bad parameters"
    (is (= 400 (:status (util/update-provider nil true false))))
    (is (= 400 (:status (util/update-provider "PROV1" nil false))))))

(deftest get-providers-test
  (util/save-provider "PROV1" false false)
  (util/save-provider "PROV2" true true)
  (let [{:keys [status providers]} (util/get-providers)
        ;; filter out the SMALL_PROV which always exists in metadata db real database
        providers (filter #(not= "SMALL_PROV" (:provider-id %)) providers)]
    (is (= status 200))
    (is (= [{:provider-id "PROV1" :cmr-only false :small false}
            {:provider-id "PROV2" :cmr-only true :small true}]
           (sort-by :provider-id providers)))))

(deftest delete-provider-test
  (testing "delete provider"
    (util/save-provider "PROV1" false false)
    (util/save-provider "PROV2" false true)
    (util/delete-provider "PROV1")
    (let [{:keys [status providers]} (util/get-providers)
          ;; filter out the SMALL_PROV which always exists in metadata db real database
          providers (filter #(not= "SMALL_PROV" (:provider-id %)) providers)]
      (is (= status 200))
      (is (= [{:provider-id "PROV2" :cmr-only false :small true}] providers))))
  (testing "delete SMALL_PROV provider"
    (let [{:keys [status errors]} (util/delete-provider "SMALL_PROV")]
      (is (= [400 ["Provider [SMALL_PROV] is a reserved provider of CMR and cannot be deleted."]]
             [status errors]))))
  (testing "delete non-existant provider"
    (let [{:keys [status errors]} (util/delete-provider "NOT_PROV")]
      (is (= [404 ["Provider with provider-id [NOT_PROV] does not exist."]]
             [status errors])))))
