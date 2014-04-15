(ns cmr.metadata-db.int-test.concepts.save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fixture
  [f]
  (try
    (util/save-provider "PROV1")
    (f)
    (finally
      (util/reset-database))))

(use-fixtures :each fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-concept-test
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [status revision-id concept-id]} (util/save-concept concept)]
    (is (= status 201))
    (is (= revision-id 0))
    (util/verify-concept-was-saved (merge concept {:revision-id revision-id :concept-id concept-id}))))

(deftest save-concept-test-with-proper-revision-id-test
  (let [concept (util/collection-concept "PROV1" 1)]
    ;; save the concept once
    (let [{:keys [revision-id concept-id]} (util/save-concept concept)
          new-revision-id (inc revision-id)]
      ;; save it again with a valid revision-id
      (let [{:keys [status revision-id]} (util/save-concept (merge concept {:revision-id new-revision-id
                                                                            :concept-id concept-id}))]
        (is (= status 201))
        (is (= revision-id new-revision-id))
        (util/verify-concept-was-saved (merge concept {:revision-id revision-id
                                                       :concept-id concept-id}))))))

(deftest save-concept-with-bad-revision-test
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)
        concept-with-bad-revision (merge concept {:concept-id concept-id :revision-id 2})
        {:keys [status]} (util/save-concept concept-with-bad-revision)
        {:keys [retrieved-concept]} (util/get-concept-by-id (:concept-id concept))
        retrieved-revision (:revision-id retrieved-concept)]
    (is (= status 409))
    (is (nil? retrieved-revision))))

(deftest save-concept-with-missing-required-parameter
  (let [concept (util/collection-concept "PROV1" 1)]
    (are [field] (let [{:keys [status error-messages]} (util/save-concept (dissoc concept field))]
                   (and (= 422 status)
                        (re-find (re-pattern (name field)) (first error-messages))))
         :concept-type
         :provider-id
         :native-id)))


(deftest save-concept-after-delete
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)]
    (util/delete-concept concept-id)
    (let [{:keys [status revision-id]} (util/save-concept concept)]
      (is (= status 201))
      (is (= revision-id 2)))))


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
          error-messages (get body "errors")]
      (is (= status 400))))
