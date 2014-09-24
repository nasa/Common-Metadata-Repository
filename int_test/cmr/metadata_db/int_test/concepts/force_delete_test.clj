(ns cmr.metadata-db.int-test.concepts.force-delete-test
  "Contains integration test for emptying database via force-delete."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (util/reset-database-fixture "PROV1"))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest force-delete-collection-test
  (let [coll (util/create-and-save-collection "PROV1" 1)
        concept-id (:concept-id coll)
        _ (dorun (repeatedly 3 #(util/save-concept (dissoc coll :revision-id))))
        {:keys [status revision-id]} (util/force-delete-concept concept-id 2)]
    (testing "revision-id correct"
      (is (= status 200))
      (is (= revision-id 2)))
    (testing "revision is gone"
      (is (= 404 (:status (util/get-concept-by-id-and-revision concept-id 2)))))
    (testing "earlier revisions still available"
      (is (util/verify-concept-was-saved (assoc coll :revision-id 1))))
    (testing "later revisions still available"
      (is (util/verify-concept-was-saved (assoc coll :revision-id 3)))
      (is (util/verify-concept-was-saved (assoc coll :revision-id 4))))
    (testing "delete non-existent revision gets 404"
      (is (= 404 (:status (util/force-delete-concept concept-id 2))))
      (is (= 404 (:status (util/force-delete-concept concept-id 22)))))))

(deftest force-delete-granule-test
  (let [coll (util/create-and-save-collection "PROV1" 1)
        gran (util/create-and-save-granule "PROV1" (:concept-id coll) 1)
        concept-id (:concept-id gran)
        _ (dorun (repeatedly 3 #(util/save-concept (dissoc gran :revision-id))))
        {:keys [status revision-id]} (util/force-delete-concept concept-id 2)]
    (testing "revision-id correct"
      (is (= status 200))
      (is (= revision-id 2)))
    (testing "revision is gone"
      (is (= 404 (:status (util/get-concept-by-id-and-revision concept-id 2)))))
    (testing "earlier revisions still available"
      (is (util/verify-concept-was-saved (assoc gran :revision-id 1))))
    (testing "later revisions still available"
      (is (util/verify-concept-was-saved (assoc gran :revision-id 3)))
      (is (util/verify-concept-was-saved (assoc gran :revision-id 4))))
    (testing "delete non-existent revision gets 404"
      (is (= 404 (:status (util/force-delete-concept concept-id 2))))
      (is (= 404 (:status (util/force-delete-concept concept-id 22)))))))

(deftest force-delete-non-existent-test
  (testing "id not exist"
    (is (= 404 (:status (util/force-delete-concept "C22-PROV1" 0))))
    (is (= 404 (:status (util/force-delete-concept "G22-PROV1" 0)))))
  (testing "provider not exist"
    (is (= 404 (:status (util/force-delete-concept "C22-PROV2" 0))))
    (is (= 404 (:status (util/force-delete-concept "G22-PROV2" 0))))))

