(ns cmr.metadata-db.int-test.reset-test
  "Contains integration test for emptying database via reset"
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "PROV1" :small false}))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest reset
  (testing "Reset the database to an empty state"
    (let [concept (concepts/create-and-save-concept :collection "PROV1" 1)
          _ (util/reset-database)
          stored-concept (util/get-concept-by-id-and-revision (:concept-id concept) 0)
          status (:status stored-concept)]
      ;; make sure the previously stored concept is not found
      (is (= status 404)))))
