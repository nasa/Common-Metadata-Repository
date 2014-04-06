(ns cmr.index-set.data.elasticsearch
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clj-http.client :as client]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cheshire.core :as cheshire]
            [cmr.index-set.config.elasticsearch-config :as es-config]
            [cmr.system-trace.core :refer [deftracefn]]))

(defn- connect-with-config
  "Connects to ES with the given config"
  [config]
  (let [{:keys [host port]} config]
    (info (format "Connecting to single ES on %s %d" host port))
    (esr/connect! (str "http://" host ":" port))))

(defn create-index
  "Create elastic index"
  [; {:keys [index-name settings mapping] :as idx-w-config}
   idx-w-config]
  (let [{:keys [index-name settings mapping]} idx-w-config]
    (when-not (esi/exists? index-name)
      (try
        (let [response (esi/create index-name :settings settings :mappings mapping)]
          #_(info "index creation attempt result:" response))
        (catch Exception e
          (.printStackTrace e)
          (errors/internal-error! (:error-message (format "error creating %s elastic index - %s" index-name (.getMessage e)))))))))

(defn get-index-set
  "Fetch index-set associated with an id. Convert stored index set json string to a map."
  [index-name es-mapping-type index-set-id]
  (when (esi/exists? index-name)
    (let [hit-doc (doc/get index-name es-mapping-type index-set-id "fields" "index-set-id,index-set-name,index-set-request")
          index-set-json-str (-> hit-doc :fields :index-set-request)]
      (cheshire.core/decode index-set-json-str true))))

(defn delete-index
  "Delete given elastic index"
  [index-name es-config]
  (when (esi/exists? index-name)
    (let [admin-token (:admin-token es-config)
          response (client/delete (esr/index-url index-name)
                                  {:headers {"Authorization" admin-token
                                             "Confirm-delete-action" "true"}
                                   :throw-exceptions false})
          status (:status response)]
      (if-not (some #{200 202 204} [status])
        (errors/internal-error! (str "Index name: " index-name " delete elasticsearch index operation failed " response))))))

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
    (create-index es-config/index-w-config)
    this)

  (stop [this system]
        this))

(defn create-elasticsearch-store
  "Creates the Elasticsearch store."
  [config]
  (map->ESstore {:config config}))


(deftracefn save-document-in-elastic
  "Save the document in Elasticsearch, raise error on failure."
  [context es-index es-mapping-type doc-id es-doc]
  (try
    (let [result (doc/put es-index es-mapping-type doc-id es-doc)]
      (if (:error result)
        (if (= 409 (:status result)) ;; this should never happen on 'put' operation but ES throws this error
          (errors/throw-service-error :conflict (str "Save to Elasticsearch failed " (str result)))
          (errors/internal-error! (str "Save to Elasticsearch failed " (str result))))))
    (catch clojure.lang.ExceptionInfo e
      (let [err-msg (get-in (ex-data e) [:object :body])
            msg (str "Call to Elasticsearch caught exception " err-msg)]
        (errors/internal-error! msg)))))

(comment
  (create-index es-config/index-w-config)
  (doc/count "index-sets" "set")
  (doc/put "index-sets" "set" "3" sample-index-set)
  (doc/get "index-sets" "set" "55" "fields" "index-set-id,index-set-name,index-set-request")
  (let [hit-doc (doc/get "index-sets" "set" "55" "fields" "index-set-id,index-set-name,index-set-request")
        index-set-json-str (-> hit-doc :fields :index-set-request)]
    (cheshire.core/decode index-set-json-str true))
  (let [data {:index-set-id (-> sample-index-set :index-set :id)
              :index-set-name (-> sample-index-set :index-set :name)
              :index-set-request (cheshire.core/generate-string sample-index-set)}]
    (doc/put "index-sets" "set" "3" data))
  (doc/put "index-sets" "set" "3"  (cheshire.core/generate-string  sample-index-set))
  )







