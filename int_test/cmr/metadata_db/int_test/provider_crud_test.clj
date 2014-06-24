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
  (testing "Save a provider that has never been saved before."
    (let [{:keys [status]} (util/save-provider "PROV1")]
      (is (= status 201))
      (is (util/verify-provider-was-saved "PROV1")))))

(deftest save-provider-twice-test
  (testing "Fail to save a provider that has been saved before."
    (util/save-provider "PROV1")
    (is (= 409 (:status (util/save-provider "PROV1"))))))

(deftest get-providers-test
  (testing "Get the list of providers."
    (util/save-provider "PROV1")
    (util/save-provider "PROV2")
    (let [{:keys [status providers]} (util/get-providers)]
      (is (= status 200))
      (is (= (sort providers) ["PROV1" "PROV2"])))))

(deftest delete-provider-test
  (testing "Delete provider removes provider"
    (util/save-provider "PROV1")
    (util/save-provider "PROV2")
    (util/delete-provider "PROV1")
    (let [{:keys [status providers]} (util/get-providers)]
      (is (= status 200))
      (is (= providers ["PROV2"])))))