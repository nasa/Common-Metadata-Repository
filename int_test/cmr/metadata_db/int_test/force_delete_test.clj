(ns cmr.metadata-db.int-test.force-delete-test
  "Contains integration test for emptying database via force-delete."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each util/reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest force-delete-test
  "Reset the database to an empty state"
  (let [concept (util/concept)
        _ (util/save-concept concept)
        _ (util/reset-database)
        stored-concept (util/get-concept-by-id-and-revision (:concept-id concept) 0)
        status (:status stored-concept)]
    ;; make sure the previously stored concept is not found
    (is (= status 404))))