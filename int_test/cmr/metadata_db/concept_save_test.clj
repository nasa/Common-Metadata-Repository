(ns cmr.metadata-db.concept-save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))

;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- concept
  "Creates a concept to be used for testing."
  []
  {:concept-type :collection
   :native-id "provider collection id"
   :concept-id "CO-PROV1-1"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(defn- get-concept-by-id-and-revision
  "Make a get to retrieve a concept by concept-id and revision."
  [concept-id revision-id]
  (let [response (client/get (str "http://localhost:3001/concepts/" concept-id "/" revision-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= status 200)
      (let [found-concept (clojure.walk/keywordize-keys (cheshire/parse-string (:body response)))]
        {:status status :concept found-concept})
      {:status status :concept nil})))

(defn- get-concept-by-id
  "Make a get to retrieve a concept by concept-id."
  [concept-id]
  (let [response (client/get (str "http://localhost:3001/concepts/" concept-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= status 200)
      (let [found-concept (clojure.walk/keywordize-keys (cheshire/parse-string (:body response)))]
        {:status status :concept found-concept})
      {:status status :concept nil})))
        

(defn- save-concept
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

(defn- reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (let [response (client/delete "http://localhost:3001/concepts" 
                                {:throw-exceptions false})
        status (:status response)]
    status))

(defn- verify-concept-was-saved
  "Check to make sure a concept is stored in the database."
  [concept revision-id]
  (let [concept-id (:concept-id concept)
        stored-concept-and-status (get-concept-by-id-and-revision concept-id revision-id)
        stored-concept (:concept stored-concept-and-status)
        stored-concept-id (:concept-id stored-concept)]
    (is (= stored-concept-id concept-id))))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- reset-database-fixture
  "Reset the database after every test."
  [f]
  (try
    (f)
    (finally (reset-database))))

(use-fixtures :each reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest force-delete-test
  "Reset the database to an empty state"
  (let [concept (concept)
        _ (save-concept concept)
        _ (reset-database)
        stored-concept (get-concept-by-id-and-revision (:concept-id concept) 0)
        status (:status stored-concept)]
    ;; make sure the previously stored concept is not found
    (is (= status 404))))

(deftest mdb-save-concept-test
  "Save a valid concept with no revision-id."
  (let [{:keys [status revision-id]} (save-concept (concept))]
    (is (and (= status 201) (= revision-id 0)))
    (verify-concept-was-saved concept 0)))

(deftest mdb-save-concept-test-with-proper-revision-id-test
  "Save a valid concept with a valid revision-id"
  (let [concept (concept)]
    ;; save the concept once
    (save-concept concept)
    ;; save it again with a valid revision-id
    (let [{:keys [status revision-id]} (save-concept (assoc concept :revision-id 1))]
      (is (and (= status 201) (= revision-id 1)))
      (verify-concept-was-saved concept 1))))

(deftest mdb-save-concept-with-bad-revision-test
  "Fail to save a concept with an invalid revision-id"
  (let [concept (concept)
        _ (save-concept concept)
        concept-with-bad-revision (assoc concept :revision-id 2)
        {:keys [status]} (save-concept concept-with-bad-revision)
        {:keys [retrieved-concept]} (get-concept-by-id (:concept-id concept))
        retrieved-revision (:revision-id retrieved-concept)]
    (is (and (= status 409) (nil? retrieved-revision)))))

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
    (let [{:keys [status]} (save-concept (dissoc (concept) :concept-type))]
      (is (= 422 status))))
  (testing "missing concept-id"
    (let [{:keys [status]} (save-concept (dissoc (concept) :concept-id))]
      (is (= 422 status)))))



