(ns cmr.ingest.data.mdb
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to metadata db."
  (:require [cmr.ingest.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [clj-http.client :as client]
            [cheshire.core :as  cheshire] 
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn metadata-db-config
  "Returns default Metadata-DB Config."
  []
  (let [host "localhost"
        port 3001
        mdb-url (str "http://" host ":" port "/concepts")]
    {:host host, :port port, :mdb-url mdb-url}))

(defn build-http-request-fn
  "Template used for building a request."
  [post-or-put-op mdb-url concept-json-str]
  {:method (keyword post-or-put-op)
   :url mdb-url
   :body concept-json-str
   :body-encoding "UTF-8"
   :content-type :json
   :socket-timeout 2000  ;; in milliseconds
   :conn-timeout 2000    ;; in milliseconds
   :accept :json})

(defn- mdb-response-handler
  "Log unexpected errors from metadata db and rethrow errors."
  [http-request]
  (try+
    (client/request http-request)
    (catch Exception e 
      (warn "Exception occurred while accessing Metadata DB: " (.getMessage e))
      (throw e))
    (catch Object o
      (warn (:throwable &throw-context) "Unexpected error while accessing Metadata DB")
      (throw+ o))))
    

(defn- store-concept
  "Store a concept in metadata db."
  [http-request]
  (let [;; response (client/request http-request)  ;; need to catch exception here
        response (mdb-response-handler http-request) 
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))


;;; datalayer protocol to access metadata-db
(defrecord Metadata-DB
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
  ;; faking a call to metadata db to get the concept-id
  [this concept-type provider-id native-id]
  (str provider-id "-" (name concept-type) "-"  native-id))
  
  (save-concept
    [this concept]              
    (let [{:keys [host port mdb-url]} (:config this)
          {:keys [concept-type concept-id provider-id native-id]} concept
          concept-with-id (assoc concept :concept-id concept-id)
          concept-json-str (cheshire/generate-string concept-with-id)
          http-request (build-http-request-fn "post" mdb-url concept-json-str)
          {:keys [status revision-id error-messages]}  (store-concept http-request)]
      revision-id)))

(defn create
  "Creates proxy to metadata db."
  []
  (map->Metadata-DB {:config (metadata-db-config)}))       

