(ns cmr.metadata-db.int-test.concepts.force-delete-test
  "Contains integration test for emptying database via force-delete."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.int-test.concepts.concept-delete-spec :as cd-spec]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV" :small true}))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest force-delete-concepts-test
  (doseq [concept-type [:collection :granule :service]]
  (cd-spec/general-force-delete-test concept-type ["REG_PROV" "SMAL_PROV"])))

(deftest force-delete-tag-test
  (cd-spec/general-force-delete-test :tag ["CMR"]))

(deftest force-delete-group-general
  (cd-spec/general-force-delete-test :access-group ["REG_PROV" "SMAL_PROV" "CMR"]))

(deftest force-delete-non-existent-test
  (testing "id not exist"
    (is (= 404 (:status (util/force-delete-concept "C22-REG_PROV" 0))))
    (is (= 404 (:status (util/force-delete-concept "G22-REG_PROV" 0))))
    (is (= 404 (:status (util/force-delete-concept "S22-REG_PROV" 0))))
    (is (= 404 (:status (util/force-delete-concept "T22-REG_PROV" 0))))
    (is (= 404 (:status (util/force-delete-concept "AG22-REG_PROV" 0)))))
  (testing "provider not exist"
    (is (= 404 (:status (util/force-delete-concept "C22-PROV3" 0))))
    (is (= 404 (:status (util/force-delete-concept "G22-PROV3" 0))))
    (is (= 404 (:status (util/force-delete-concept "S22-PROV3" 0))))
    (is (= 404 (:status (util/force-delete-concept "T22-PROV3" 0))))
    (is (= 404 (:status (util/force-delete-concept "AG22-PROV3" 0))))))
