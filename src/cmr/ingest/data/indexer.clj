(ns cmr.ingest.data.indexer
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to indexer app."
  (:require [cmr.ingest.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [clj-http.client :as client]
            [cheshire.core :as  cheshire] 
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn indexer-config
  "Returns default Indexer-DB Config."
  []
  (let [host "localhost"
        port 3004
        indexer-url (str "http://" host ":" port "/concepts")]
    {:host host, :port port, :indexer-url indexer-url}))

(defn build-http-request-fn
  "Template used for building a request."
  [post-or-put-op indexer-url concept-attribs-json-str]
  {:method (keyword post-or-put-op)
   :url indexer-url
   :body concept-attribs-json-str
   :body-encoding "UTF-8"
   :content-type :json
   :socket-timeout 2000  ;; in milliseconds
   :conn-timeout 2000    ;; in milliseconds
   :accept :json})

(defn- indexer-response-handler
  "Log unexpected errors from indexer app and rethrow errors."
  [http-request]
  (try+
    ;; suppress errors until indexer service is up 
    (client/request http-request)
    (catch Exception e 
      (warn "Exception occurred while accessing indexer app: " (.getMessage e))
      #_(throw e)
      {:status 201})
    (catch Object o
      (warn (:throwable &throw-context) "Unexpected error while accessing indexer app")
      #_(throw+ o)
      {:status 201})))
    
(defn- stage-concept
  "Submit a concept for indexing."
  [http-request]
  (let [;; response (client/request http-request)  ;; need to catch exception here
        response (indexer-response-handler http-request) 
        status (:status response)
        body (cheshire/parse-string (:body response))
        error-messages (get body "errors")]
    {:status status :error-messages error-messages}))


;;; datalayer protocol to access indexer db
(defrecord Indexer-DB
  [config]
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle
  
  (start [this system]
         this)
  
  (stop [this system]
        this)
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data/ConceptIndexStore
  
  (stage-concept-for-indexing
    [this concept-id revision-id]              
    (let [{:keys [host port indexer-url]} (:config this)
          concept-attribs (into {} [[:concept-id concept-id] [:revision-id revision-id]])
          concept-attribs-json-str (cheshire/generate-string concept-attribs)
          http-request (build-http-request-fn "post" indexer-url concept-attribs-json-str)
          {:keys [status error-messages]}  (stage-concept http-request)]
      status)))

(defn create
  "Create proxy to indexer app."
  []
  (map->Indexer-DB {:config (indexer-config)}))       

