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

;; (cheshire/generate-string (concept) )
(deftest mdb-create-concept-test
  (let [ response (client/post "http://localhost:3000/concepts" 
                               {:body (cheshire/generate-string (concept) )
                                :body-encoding "UTF-8"
                                :content-type :json
                                :accept :json
                                :throw-exceptions false})
        status (:status response)]
    (is (= status 201))))
