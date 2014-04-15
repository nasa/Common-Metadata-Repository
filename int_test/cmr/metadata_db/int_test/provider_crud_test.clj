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
  "Save a provider that has never been saved before."
  (let [provider-id util/sample-provider-id
        {:keys [status provider-id]} (util/save-provider provider-id)]
    (is (= status 201))
    (is (util/verify-provider-was-saved provider-id))))

(deftest save-provider-twice-test
  "Fail to save a provider that has been saved before."
  (let [provider-id util/sample-provider-id
        _ (util/save-provider provider-id)
        {:keys [status provider-id]} (util/save-provider provider-id)]
    (is (= status 409))))

(deftest get-providers-test
  "Get the list of providers."
  (util/save-provider "PROV1")
  (util/save-provider "PROV2")
  (let [{:keys [status providers]} (util/get-providers)]
    (is (= status 200))
    (is (= (sort providers) ["PROV1" "PROV2"]))))

(deftest delete-provider-test
  (testing "Delete provider removes provider"
    (util/save-provider "PROV1")
    (util/save-provider "PROV2")
    (util/delete-provider "PROV1")
    (let [{:keys [status providers]} (util/get-providers)]
      (is (= status 200))
      (is (= providers ["PROV2"]))))
  (testing "Delete provider that doesn't exist"
    (let [{:keys [status error-messages]} (util/delete-provider "PROV3")]
      (is (= status 404))
      (is (= (first error-messages) (messages/provider-does-not-exist "PROV3"))))))