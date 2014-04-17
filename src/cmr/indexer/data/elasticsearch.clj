(ns cmr.indexer.data.elasticsearch
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.data.elasticsearch-properties :as es-prop]
            [cmr.system-trace.core :refer [deftracefn]]))


;; TODO - have functionality in indexer app to use multiple indices of a concept
;;indexer app to fetch and remember info from index-set app only at the start up time
;; map of concept-type to elasticsearch index name
(def es-concept-indices (atom {}))


;;indexer app to fetch and remember info from index-set app only at the start up time
;; map of concept-type to elasticsearch index type
(def es-concept-mapping-types (atom {}))

(defn- connect-with-config
  "Connects to ES with the given config"
  [config]
  (let [{:keys [host port]} config]
    (info (format "Connecting to single ES on %s %d" host port))
    (esr/connect! (str "http://" host ":" port))))


(defn  get-index-set
  "submit a request to index-set app to fetch an index-set assoc with an id"
  [id]
  (let [response (client/request
                   {:method :get
                    :url (format "%s/%s" es-prop/index-set-url (str id))
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        fetched-index-set (cheshire/decode (:body response) true)
        errors-str (cheshire/generate-string (flatten (get body "errors")))]
    {:status status :errors-str errors-str :fetched-index-set fetched-index-set :response response}))

(defn  create-index-set-req
  "submit a request to index-set app to create indices"
  [idx-set]
  (let [response (client/request
                   {:method :post
                    :url es-prop/index-set-url
                    :body (cheshire.core/generate-string idx-set)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))]
    {:status status :errors-str errors-str :response response}))

(defn create-index-set
  "submit a request to create index-set"
  [index-set]
  (let [{:keys [status errors-str response]} (create-index-set-req index-set)]
    (when-not (= 201 status)
      (errors/internal-error! (format "Failed to create index-set: %s, errors: %s, index-set app response: %s"
                                      (cheshire/generate-string index-set) errors-str response)))))

(defn set-concept-indices-info
  "fetch index names and mapping types for each concept type from index-set app"
  [index-set-id]
  (let [{:keys [status fetched-index-set errors-str response]} (get-index-set index-set-id)]
    (when-not (= 200 status)
      (errors/internal-error! (format "index-set with id: %s not found. index-set app reported errors: %s, response: %s"
                                      index-set-id errors-str response)))
    (reset!  es-concept-indices {:collection (first (vals (get-in fetched-index-set [:index-set :concepts :collection])))
                                 :granule (first (vals (get-in fetched-index-set [:index-set :concepts :granule])))})
    (reset! es-concept-mapping-types {:collection (name (first (keys (get-in fetched-index-set [:index-set :collection :mapping]))))
                                      :granule (name (first (keys (get-in fetched-index-set [:index-set :granule :mapping]))))})))

(defn- create-indexes
  "Create elastic index for each index name"
  []
  (let [index-set es-prop/index-set
        index-set-id (get-in index-set [:index-set :id])
        {:keys [status]} (get-index-set index-set-id)]
    (when (= 404 status)
      (info "Creating index-set: " (cheshire/generate-string index-set))
      (create-index-set index-set))
    (set-concept-indices-info index-set-id)))


(defn- delete-indexes
  "Delete configured elastic indexes"
  []
  (let [response (client/request
                   {:method :post
                    :url es-prop/index-set-reset-url
                    :content-type :json
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))]
    {:status status :errors-str errors-str :response response}))


(defn reset-es-store
  "Delete elasticsearch indexes and re-create them via index-set app. A nuclear option just for the development team."
  []
  (delete-indexes)
  (create-indexes))


(defrecord ESstore
  [
   ;; configuration of host, port and admin-token for elasticsearch
   config
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (connect-with-config (:config this))
    (create-indexes)
    this)

  (stop [this system]
        this))

(defn create-elasticsearch-store
  "Creates the Elasticsearch store."
  [config]
  (map->ESstore {:config config}))

(defn- try-elastic-operation
  "Attempt to perform the operation in Elasticsearch, handles exceptions.
  f is the operation function to Call
  es-index is the elasticsearch index name
  es-mapping is the elasticsearch mapping
  es-doc is the elasticsearch document to be passed on to elasticsearch
  revision-id is the version of the document in elasticsearch"
  [f es-index es-mapping es-doc revision-id]
  (try
    (f es-index es-mapping (:concept-id es-doc) es-doc :version revision-id :version_type "external")
    (catch clojure.lang.ExceptionInfo e
      (let [err-msg (get-in (ex-data e) [:object :body])
            msg (str "Call to Elasticsearch caught exception " err-msg)]
        (errors/internal-error! msg)))))

(deftracefn save-document-in-elastic
  "Save the document in Elasticsearch, raise error if failed."
  [context es-index es-mapping es-doc revision-id ignore-conflict]
  (let [result (try-elastic-operation doc/put es-index es-mapping es-doc revision-id)]
    (if (:error result)
      (if (= 409 (:status result))
        (if ignore-conflict
          (info (str "Ignore conflict: " (str result)))
          (errors/throw-service-error :conflict (str "Save to Elasticsearch failed " (str result))))
        (errors/internal-error! (str "Save to Elasticsearch failed " (str result)))))))

(deftracefn get-document
  "Get the document from Elasticsearch, raise error if failed."
  [context es-index es-mapping id]
  (doc/get es-index es-mapping id))

(deftracefn delete-document-in-elastic
  "Delete the document from Elasticsearch, raise error if failed."
  [context es-config es-index es-mapping id revision-id ignore-conflict]
  ;; Cannot use elastisch for deletion as we require special headers on delete
  #_(let [result (try-elastic-operation doc/delete es-index es-mapping id)]
      (if (not (:ok result))
        (errors/internal-error! (str "Delete from Elasticsearch failed " (str result)))))
  (let [{:keys [host port admin-token]} es-config
        delete-url (format "http://%s:%s/%s/%s/%s?version=%s&version_type=external" host port es-index es-mapping id revision-id)
        response (client/delete delete-url
                                {:headers {"Authorization" admin-token
                                           "Confirm-delete-action" "true"}
                                 :throw-exceptions false})
        status (:status response)]
    (if-not (some #{200 404} [status])
      (if (= 409 status)
        (if ignore-conflict
          (info (str "Ignore conflict: " (str response)))
          (errors/throw-service-error :conflict (str "Delete from Elasticsearch failed " (str response))))
        (errors/internal-error! (str "Delete from Elasticsearch failed " (str response)))))))





