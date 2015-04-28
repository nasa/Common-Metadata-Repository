(ns cmr.metadata-db.int-test.provider-crud-test
  "Contains integration tests for deleting providers. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-provider-test
  (testing "with cmr-only false"
    (let [{:keys [status]} (util/save-provider "PROV1" false)]
      (is (= status 201))
      (is (util/verify-provider-was-saved "PROV1" false))))
  (testing "save provider twice"
    (is (= 409 (:status (util/save-provider "PROV1")))))
  (testing "with cmr-only true"
    (let [{:keys [status]} (util/save-provider "PROV2" true)]
      (is (= status 201))
      (is (util/verify-provider-was-saved "PROV2" true))))
  (testing "without cmr-only"
    (let [{:keys [status]} (util/save-provider "PROV3" nil)]
      (is (= status 201))
      ;; cmr-only defaults to false
      (is (util/verify-provider-was-saved "PROV3" false)))))

(deftest get-providers-test
  (util/save-provider "PROV1")
  (util/save-provider "PROV2")
  (let [{:keys [status providers]} (util/get-providers)]
    (is (= status 200))
    (is (= [{:provider-id "PROV1" :cmr-only false}
            {:provider-id "PROV2" :cmr-only false}]
           (sort-by :provider-id providers)))))

(deftest delete-provider-test
  (util/save-provider "PROV1")
  (util/save-provider "PROV2")
  (util/delete-provider "PROV1")
  (let [{:keys [status providers]} (util/get-providers)]
    (is (= status 200))
    (is (= [{:provider-id "PROV2" :cmr-only false}] providers))))

(deftest delete-nonexistant-provider-test
  (is (= 404 (:status (util/delete-provider "PROV1")))))
