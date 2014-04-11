(ns cmr.metadata-db.int-test.provider-save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each util/reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-save-provider-test
  "Save a provider that has never been saved before."
  (let [provider-id util/sample-provider-id
        {:keys [status provider-id]} (util/save-provider provider-id)]
    (is (= status 201))
    (is (util/verify-provider-was-saved provider-id))))

(deftest mdb-save-provider-twice-test
  "Fail to save a provider that has been saved before."
  (let [provider-id util/sample-provider-id
        _ (util/save-provider provider-id)
        {:keys [status provider-id]} (util/save-provider provider-id)]
    (is (= status 409))))
