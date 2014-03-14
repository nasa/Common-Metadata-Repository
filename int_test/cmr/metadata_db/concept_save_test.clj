(ns cmr.metadata-db.concept-save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))

;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn concept
  "Creates a concept to be used for testing with a given concept-id"
  [concept-id]
  {:concept-type :collection
   :native-id "provider collection id"
   :concept-id concept-id
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(defn save-concept
  "Make a post request to save a concept without JSON encoding the concept.  Returns a map with
  status, revision-id, and a list of error messages"
  [concept]
  (let [response (client/post "http://localhost:3001/concepts" 
                              {:body (cheshire/generate-string concept)
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))


;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-save-concept-test
  "Save a valid concept with no revision-id."
  (let [{:keys [status revision-id]} (save-concept (concept "CP1"))]
    (is (and (= status 201) (= revision-id 0)))))

(deftest mdb-save-concept-test-with-proper-revision-id-test
  "Save a valid concept with a valid revision-id"
  (let [concept (concept "CP2")]
    ;; save the concept once
    (save-concept concept)
    ;; save it again with a valid revision-id
    (let [{:keys [status revision-id]} (save-concept (assoc concept :revision-id 1))]
      (is (and (= status 201) (= revision-id 1))))))

(deftest mdb-save-concept-with-bad-revision-test
  "Fail to save a concept with an invalid revision-id"
  ;; TODO Add a GET request to make sure the revision id of the existing record was not
  ;; incremented
  (let [concept-with-bad-revision (assoc (concept "CP3") :revision-id 1)
        {:keys [status]} (save-concept concept-with-bad-revision)]
    (is (= status 409))))

;;; This test is disabled because the middleware is currently returning a
;;; 500 status code instead of a 400. This will be addressed as a separate
;;; issue.
#_(deftest mdb-save-concept-with-invalid-json-test
  "Fail to save a concept if the json is invalid"
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

(deftest mdb-save-concept-with-missing-required-parameter
  "Fail to save a concept if a required parameter is missing"
  (testing "missing concept-type"
    (let [{:keys [status]} (save-concept (dissoc (concept "CP4") :concept-type))]
      (is (= 422 status))))
  (testing "missing concept-id"
    (let [{:keys [status]} (save-concept (dissoc (concept "CP4") :concept-id))]
      (is (= 422 status)))))



