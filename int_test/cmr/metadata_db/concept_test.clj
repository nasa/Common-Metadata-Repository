(ns cmr.metadata-db.concept-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))


;; TODO add a new endpoint to generate a concept id
;; - takes concept type and provider id



(defn concept
  [concept-id]
  {:concept-type :collections
   :native-id "provider collection id"
   :concept-id concept-id
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(deftest mdb-save-concept-test
  (client/post "http://localhost:3000/concepts" 
               {:body (cheshire/generate-string (concept "CP1") )
                :body-encoding "UTF-8"
                :content-type :json
                :accept :json
                :throw-exceptions false})
  (let [response (client/post "http://localhost:3000/concepts" 
                              {:body (cheshire/generate-string (concept "CP1") )
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")]
    (println "BODY: " body)
    (println "REVISION-ID: " revision-id)
    (is (and (= status 201) (= revision-id 1)))))

(deftest mdb-save-concept-test-with-proper-revision-id
 (client/post "http://localhost:3000/concepts" 
               {:body (cheshire/generate-string (concept "CP2") )
                :body-encoding "UTF-8"
                :content-type :json
                :accept :json
                :throw-exceptions false})
  (let [response (client/post "http://localhost:3000/concepts" 
                              {:body (cheshire/generate-string (assoc (concept "CP2") :revision-id 1))
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")]
    (println "BODY: " body)
    (println "REVISION-ID: " revision-id)
    (is (and (= status 201) (= revision-id 1)))))

(deftest mdb-save-concept-with-bad-revision-test
  (let [concept-with-bad-revision (assoc (concept "CP3") :revision-id 5)
        response (client/post "http://localhost:3000/concepts" 
                              {:body (cheshire/generate-string concept-with-bad-revision)
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")]
    (println "BODY: " body)
    (println "REVISION-ID: " revision-id)
    (is (and (= status 409) (= revision-id 0)))))
    