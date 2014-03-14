(ns cmr.ingest.test.concepts
  (:require [clojure.test :refer :all]
            [cmr.common.log :as log :refer (debug info warn error)]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))

(deftest ping-ingest-app-test
  (let [response (client/get "http://localhost:3002/providers" )
        status (:status response)]
    (is (= status 200))))


(defn concept
  "Creates a concept to be used for testing with a given concept-id"
  [concept-id]
  {
   :native-id "provider collection id"
   :concept-id concept-id
   :metadata "xml here"
   :format "iso19115"})

(defn save-concept-without-json
  "Make a put request to ingest a concept without JSON encoding the concept.  Returns a map with
  status, revision-id, and a list of error messages"
  [concept]
  (let [response (client/request
                   {:method :put
                    :url "http://localhost:3002/providers/PROV77/collections/nativeID1" 
                    :body concept
                    :body-encoding "UTF-8"
                    :content-type :json
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))

(defn save-concept 
  "Make a post request to save a concept. Returns a map with status, revision-id, and a list of
  error messages."
  [concept]
  (save-concept-without-json (cheshire/generate-string concept)))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest concept-ingest-test-1
  "Save a valid concept with no revision-id."
  (let [{:keys [status revision-id]} (save-concept (concept "CP88"))]
    (is (and (= status 201) (= revision-id 0)))))

(deftest concept-ingest-test-2
  "Save a valid concept with no revision-id."
  (let [{:keys [status revision-id]} (save-concept (concept "CP88"))]
    (is (and (= status 201) (= revision-id 1)))))



