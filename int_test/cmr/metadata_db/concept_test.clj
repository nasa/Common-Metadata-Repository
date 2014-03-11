(ns cmr.metadata-db.concept-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))


(defn concept
  []
  {:concept-type :collection
   :id "echo coll id"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(deftest mdb-create-concept-test
  (let [response (client/post "http://localhost:3000/concepts" 
                               {:body (cheshire/generate-string (concept) )
                                :body-encoding "UTF-8"
                                :content-type :json
                                :accept :json
                                :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")]
    (println "BODY: " body)
    (println "REVISION-ID: " revision-id)
    (is (and (= status 201) (> revision-id -1)))))

(deftest mdb-update-concept-test
  (client/post "http://localhost:3000/concepts" 
                               {:body (cheshire/generate-string (concept) )
                                :body-encoding "UTF-8"
                                :content-type :json
                                :accept :json
                                :throw-exceptions false})
  (let [response (client/post "http://localhost:3000/concepts" 
                               {:body (cheshire/generate-string (concept) )
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

(comment
  (let [val "100"
        ival (Integer/parseInt val)]
    (println "ival = " ival)
  )
  (get (concept) :revision-id)
  
  )
