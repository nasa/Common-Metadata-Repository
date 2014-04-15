(ns cmr.metadata-db.int-test.reset-test
  "Contains integration test for emptying database via reset"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each util/reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest reset
  "Reset the database to an empty state"
  (let [concept (util/collection-concept "PROV1" 1)
        _ (util/save-concept concept)
        _ (util/reset-database)
        stored-concept (util/get-concept-by-id-and-revision (:concept-id concept) 0)
        status (:status stored-concept)]
    ;; make sure the previously stored concept is not found
    (is (= status 404))))