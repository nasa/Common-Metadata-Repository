(ns cmr.ingest.data.mdb
  "Implements Ingest App datalayer access interface. Takes on the role of proxy to metadata db."
  (:require [cmr.ingest.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [clj-http.client :as client]
            [cheshire.core :as  cheshire] 
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]))


;; cmr phase1 temporary service ports
(def service-ports
  {:metadata-db 3001
   :ingest 3002
   :search 3003
   :indexer 3004})

;; expand to  viz, param, service types later
(def concept-types
  {:collections "collections"
   :granules "granules"})


(defn metadata-db-config
  "Returns default Metadata-DB Config. Anticipate multiple endpoints."
  []
  {:db "metadata-db"
   :endpoint {:host "localhost"
              :port (:metadata-db service-ports)}})

(defn build-http-request-fn
  "given an url and json body string return a put request"
  [mdb-url concept-json-str]
  {:method :post
   :url mdb-url
   :body concept-json-str
   :body-encoding "UTF-8"
   :content-type :json
   :socket-timeout 2000  ;; in milliseconds
   :conn-timeout 2000    ;; in milliseconds
   :accept :json})

(defn- store-concept 
  "store a concept in metadata db"
  [http-request]
  (let [response (client/request http-request)  ;; need to catch exception here
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))


;; datalayer protocol to access metadata-db
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
  
  ;; TODO check for concept existence in metadata db
  
  (save-concept
    [this concept]              
    (let [{{host :host} :endpoint
           {port :port} :endpoint
           build-http-request-fn :build-http-request-fn} (:config this)
          mdb-url (str "http://" host ":" port "/concepts")
          concept-json-str (cheshire/generate-string concept)
          http-request (build-http-request-fn mdb-url concept-json-str)
          {:keys [status revision-id error-messages]}  (store-concept http-request)]
      (info  status)
      (info revision-id)
      (if-not ( and (= status 201)  (not (neg? revision-id)))
        (errors/throw-service-error :conflict 
                                    "metadata db response status %s and error messages %s"
                                    status
                                    error-messages))
      revision-id)))

(defn create
  "Creates proxy to metadata db"
  []
  (map->Metadata-DB {:config (assoc (metadata-db-config) :build-http-request-fn build-http-request-fn)}))       

