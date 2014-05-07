(ns cmr.metadata-db.int-test.concepts.save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [cmr.metadata-db.int-test.utility :as util]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture "PROV1"))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-collection-test
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [status revision-id concept-id]} (util/save-concept concept)]
    (is (= 201 status))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc concept :revision-id revision-id :concept-id concept-id)))))

(deftest save-granule-test
  (let [collection (util/collection-concept "PROV1" 1)
        parent-collection-id (:concept-id (util/save-concept collection))
        granule (util/granule-concept "PROV1" parent-collection-id 1)
        {:keys [status revision-id concept-id]} (util/save-concept granule)]
    (is (= 201 status))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))))

(deftest save-concept-test-with-proper-revision-id-test
  (let [concept (util/collection-concept "PROV1" 1)]
    ;; save the concept once
    (let [{:keys [revision-id concept-id]} (util/save-concept concept)
          new-revision-id (inc revision-id)
          revision-date-0 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                  [:concept :revision-date])]
      ;; save it again with a valid revision-id
      (let [updated-concept (assoc concept :revision-id new-revision-id :concept-id concept-id)
            {:keys [status revision-id]} (util/save-concept updated-concept)
            revision-date-1 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                    [:concept :revision-date])]
        (is (= 201 status))
        (is (= revision-id new-revision-id))
        (is (t/after? revision-date-1 revision-date-0))
        (is (util/verify-concept-was-saved updated-concept))))))

(deftest save-concept-with-bad-revision-test
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)
        concept-with-bad-revision (assoc concept :concept-id concept-id :revision-id 3)
        {:keys [status]} (util/save-concept concept-with-bad-revision)
        {:keys [retrieved-concept]} (util/get-concept-by-id (:concept-id concept))
        retrieved-revision (:revision-id retrieved-concept)]
    (is (= 409 status))
    (is (nil? retrieved-revision))))

(deftest save-concept-with-missing-required-parameter
  (let [concept (util/collection-concept "PROV1" 1)]
    (are [field] (let [{:keys [status errors]} (util/save-concept (dissoc concept field))]
                   (and (= 422 status)
                        (re-find (re-pattern (name field)) (first errors))))
         :concept-type
         :provider-id
         :native-id
         :extra-fields)))

(deftest save-concept-after-delete
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)]
    (is (= 200 (:status (util/delete-concept concept-id))))
    (let [{:keys [status revision-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 3)))))

(deftest save-granule-with-concept-id
  (let [collection (util/collection-concept "PROV1" 1)
        parent-collection-id (:concept-id (util/save-concept collection))
        granule (util/granule-concept "PROV1" parent-collection-id 1 "G10-PROV1")
        {:keys [status revision-id concept-id]} (util/save-concept granule)]
    (is (= 201 status))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))))

(deftest save-granule-with-nil-required-field
  (testing "nil parent-collection-id"
    (let [granule (util/granule-concept "PROV1" nil 1)
          {:keys [status revision-id concept-id]} (util/save-concept granule)]
      (is (= 422 status))
      (is (not (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))))))

;;; TODO - add test for saving concept with concept-type, provider-id, and native-id
;;; of existing concept but with different concept-id to be sure it fails.

;;; This test is disabled because the middleware is currently returning a
;;; 500 status code instead of a 400. This will be addressed as a separate
;;; issue.
#_(deftest save-concept-with-invalid-json-test
    (let [response (client/post "http://localhost:3000/concepts"
                                {:body "some non-json"
                                 :body-encoding "UTF-8"
                                 :content-type :json
                                 :accept :json
                                 :throw-exceptions false})
          status (:status response)
          body (cheshire/parse-string (:body response))
          errors (get body "errors")]
      (is (= 400 status))))

