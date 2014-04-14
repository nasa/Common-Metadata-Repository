(ns cmr.metadata-db.int-test.delete-provider-test
  "Contains integration tests for deleting providers. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each util/reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
      (is (= (first error-messages) (messages/provider-does-not-exist-msg "PROV3"))))))