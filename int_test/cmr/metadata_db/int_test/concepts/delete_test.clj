(ns cmr.metadata-db.int-test.concepts.delete-test
  "Contains integration tests for deleting concepts. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (util/reset-database-fixture "PROV1"))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest delete-collection-test
  (let [coll1 (util/create-and-save-collection "PROV1" 1 3)
        gran1 (util/create-and-save-granule "PROV1" (:concept-id coll1) 1 2)
        coll2 (util/create-and-save-collection "PROV1" 2)
        gran3 (util/create-and-save-granule "PROV1" (:concept-id coll2) 1)
        {:keys [status revision-id]} (util/delete-concept (:concept-id coll1))]
    (is (= status 200))
    (is (= revision-id 4))
    (util/verify-concept-was-deleted (:concept-id coll1) revision-id)

    ;; Verify granule was deleted
    (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 1)))
    (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 2)))

    ;; Other data left in database
    (is (util/verify-concept-was-saved coll2))
    (is (util/verify-concept-was-saved gran3))))

(deftest delete-collection-with-valid-revision-test
  (let [coll1 (util/create-and-save-collection "PROV1" 1 3)
        {:keys [status revision-id]} (util/delete-concept (:concept-id coll1) 4)]
    (is (= status 200))
    (is (= revision-id 4))))

(deftest delete-granule-test
  (let [parent-coll-id (:concept-id (util/create-and-save-collection "PROV1" 1))
        gran1 (util/create-and-save-granule "PROV1" parent-coll-id 1 3)
        gran2 (util/create-and-save-granule "PROV1" parent-coll-id 2)
        {:keys [status revision-id]} (util/delete-concept (:concept-id gran1))]
    (is (= status 200))
    (is (= revision-id 4))
    (util/verify-concept-was-deleted (:concept-id gran1) revision-id)

    ;; Other data left in database
    (is (util/verify-concept-was-saved gran2))))

(deftest delete-granule-with-valid-revision-test
  (let [parent-coll-id (:concept-id (util/create-and-save-collection "PROV1" 1))
        gran1 (util/create-and-save-granule "PROV1" parent-coll-id 1 3)
        {:keys [status revision-id]} (util/delete-concept (:concept-id gran1) 4)]
    (is (= status 200))
    (is (= revision-id 4))))

(deftest delete-concept-with-invalid-revision
  (let [coll1 (util/create-and-save-collection "PROV1" 1)
        {:keys [status]} (util/delete-concept (:concept-id coll1) 3)]
    (is (= status 409))))

(deftest fail-to-delete-missing-concept
  (let [{:keys [status revision-id errors]} (util/delete-concept "C100-PROV1")]
    (is (= status 404))
    (is (= errors [(messages/concept-does-not-exist "C100-PROV1")]))))

(deftest fail-to-delete-missing-concept-for-missing-provider
  (let [{:keys [status revision-id errors]} (util/delete-concept "C100-NONEXIST")]
    (is (= status 404))
    (is (= errors [(messages/providers-do-not-exist ["NONEXIST"])]))))

(deftest repeated-calls-to-delete-get-same-revision
  (let [coll1 (util/create-and-save-collection "PROV1" 1)]
    (is (= (util/delete-concept (:concept-id coll1))
           (util/delete-concept (:concept-id coll1))
           (util/delete-concept (:concept-id coll1))))))
