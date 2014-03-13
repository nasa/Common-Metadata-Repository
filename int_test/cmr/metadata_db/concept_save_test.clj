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
  {:concept-type :collections
   :native-id "provider collection id"
   :concept-id concept-id
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(defn do-post 
  "Make a post request to save a concept. Returns a map with status, revision-id, and error-code."
  [concept]
  (let [response (client/post "http://localhost:3000/concepts" 
                              {:body (cheshire/generate-string concept)
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-code (get body "error-code")]
    {:status status :revision-id revision-id :error-code error-code}))
  
;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-save-concept-test
  "Save a valid concept with no revision-id."
  (client/post "http://localhost:3000/concepts" 
               {:body (cheshire/generate-string (concept "CP1") )
                :body-encoding "UTF-8"
                :content-type :json
                :accept :json
                :throw-exceptions false})
  (let [{:keys [status revision-id error-code]} (do-post (concept "CP1"))]
    (is (and (= status 201) (= revision-id 1)))))

(deftest mdb-save-concept-test-with-proper-revision-id
  "Save a valid concept with a valid revision-id"
  (let [concept (concept "CP2")]
    ;; save the concept once
    (do-post concept)
    ;; save it again with a valid revision-id
    (let [{:keys [status revision-id error-code]} (do-post (assoc concept :revision-id 1))]
      (is (and (= status 201) (= revision-id 1))))))
    
(deftest mdb-save-concept-with-bad-revision-test
  "Fail to save a concept with an invalid revision-id"
  (let [concept-with-bad-revision (assoc (concept "CP3") :revision-id 5)
        {:keys [status revision-id error-code]} (do-post concept-with-bad-revision)]
    (is (and (= status 409) (= revision-id 0)))))
    