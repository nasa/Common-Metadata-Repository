(ns cmr.ingest.data.mdb
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to metadata db."
  (:require [cmr.ingest.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [clj-http.client :as client]
            [cheshire.core :as  cheshire] 
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]))

(defn metadata-db-config
  "Returns default MetadatDb config."
  []
  (let [host "localhost"
        port 3001
        mdb-base-url (str "http://" host ":" port)
        mdb-url (str mdb-base-url "/concepts")
        mdb-concept-id-url (str mdb-base-url "/concept-id")]
    {:host host, :port port, :mdb-base-url mdb-base-url, :mdb-url mdb-url, :mdb-concept-id-url mdb-concept-id-url}))

(defn build-http-request-fn
  "Template used for building a request."
  [post-or-put-op mdb-url concept-json-str]
  {:method post-or-put-op
   :url mdb-url
   :body concept-json-str
   :body-encoding "UTF-8"
   :content-type :json
   :socket-timeout 2000  ;; in milliseconds
   :conn-timeout 2000    ;; in milliseconds
   :accept :json})

#_(defn- store-concept-in-mdb
  "Store a concept in metadata db."
  [http-request]
  (let [response (client/request http-request) 
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")]
    (when-not (= 201 status)
      (errors/internal-error! (str "Save concept failed. Metadata DB app response status code: "  status (str response))))
    {:revision-id revision-id}))

#_(defn- fetch-concept-id
  "Fetch concept id from metadata db."
  [url concept-type provider-id native-id]
  (let [response (client/get (str url "/" (name concept-type) "/" provider-id "/" native-id)) 
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body "concept-id")]
    (when-not (= 200 status)
      (errors/internal-error! (str "Concept Id fetch failed. Metadata DB app response status code: "  status (str response))))
    {:concept-id concept-id}))

#_(defn- delete-target-concept
  "Delete concept from metadata db."
  [url concept-id]
  (let [response (client/delete (str url "/" concept-id)) 
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")]
    (when-not (= 200 status)
      (errors/internal-error! (str "Delete concept operation failed. Metadata DB app response status code: "  status (str response))))
    {:revision-id revision-id}))

;;; datalayer protocol to access metadata-db
(defrecord MetadataDb 
  [config]
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle
  
  (start [this system]
         this)
  
  (stop [this system]
        this)
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data/ConceptStore
  
  (get-concept-id
    [this concept-type provider-id native-id]
      (let [url (:mdb-concept-id-url (:config this))
            response (client/get (str url "/" (name concept-type) "/" provider-id "/" native-id)) 
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body "concept-id")]
    (when-not (= 200 status)
      (errors/internal-error! (str "Concept id fetch failed. MetadataDb app response status code: "  status (str response))))
    {:concept-id concept-id}))
  
  (save-concept
    [this concept]              
    (let [{:keys [mdb-url]} (:config this)
          concept-json-str (cheshire/generate-string concept)
          http-request (build-http-request-fn :post mdb-url concept-json-str)
          response (client/request http-request) 
          status (:status response)
          body (cheshire/parse-string (:body response))
          revision-id (get body "revision-id")]
      (when-not (= 201 status)
        (errors/internal-error! (str "Save concept failed. MetadataDb app response status code: "  status (str response))))
      {:revision-id revision-id}))

  (delete-concept
    [this concept-id]              
    (let [{:keys [mdb-url]} (:config this)
          response (client/delete (str mdb-url "/" concept-id))
          status (:status response)
          body (cheshire/parse-string (:body response))
          revision-id (get body "revision-id")]
      (when-not (= 200 status)
        (errors/internal-error! (str "Delete concept operation failed. MetadataDb app response status code: "  status (str response))))
      {:revision-id revision-id})))
          
 
(defn create
  "Creates proxy to metadata db."
  []
  (map->MetadataDb {:config (metadata-db-config)}))       


