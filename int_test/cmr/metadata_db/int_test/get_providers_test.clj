(ns cmr.metadata-db.int-test.get-providers-test
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
(deftest mdb-get-providers-test
  "Get the list of providers."
  (util/save-provider "PROV1")
  (util/save-provider "PROV2")
  (let [{:keys [status providers]} (util/get-providers)]
    (is (= status 200))
    (is (= (sort providers) ["PROV1" "PROV2"]))))