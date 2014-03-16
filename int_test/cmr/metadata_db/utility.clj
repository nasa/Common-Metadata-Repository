(ns cmr.metadata-db.utility
  "Contains various utitiltiy methods to support integeration tests."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))

;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn concept
  "Creates a concept to be used for testing."
  []
  {:concept-type :collection
   :native-id "provider collection id"
   :concept-id "CO-PROV1-1"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(defn get-concept-by-id-and-revision
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

(defn get-concept-by-id
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

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (let [response (client/delete "http://localhost:3001/concepts" 
                                {:throw-exceptions false})
        status (:status response)]
    status))

(defn verify-concept-was-saved
  "Check to make sure a concept is stored in the database."
  [concept revision-id]
  (let [concept-id (:concept-id concept)
        stored-concept-and-status (get-concept-by-id-and-revision concept-id revision-id)
        stored-concept (:concept stored-concept-and-status)
        stored-concept-id (:concept-id stored-concept)]
    (is (= stored-concept-id concept-id))))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reset-database-fixture
  "Reset the database after every test."
  [f]
  (try
    (f)
    (finally (reset-database))))