(ns cmr.indexer.data.index-set
  (:require [cmr.common.lifecycle :as lifecycle]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.data.elasticsearch-properties :as es-prop]
            [cmr.system-trace.core :refer [deftracefn]]))

(defn  get-index-set
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  [id]
  (let [response (client/request
                   {:method :get
                    :url (format "%s/%s" es-prop/index-set-url (str id))
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))]
    (if (= 200 status)
      {:status status :fetched-index-set (cheshire/decode (:body response) true)}
      {:status status :errors-str (cheshire/generate-string (flatten (get body "errors")))})))

(defn  create-index-set-req
  "Submit a request to index-set app to create indices"
  [idx-set]
  (let [response (client/request
                   {:method :post
                    :url es-prop/index-set-url
                    :body (cheshire.core/generate-string idx-set)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))]
    (if (= 200 status)
      {:status status :body body}
      {:status status :errors-str (cheshire/generate-string (flatten (get body "errors")))})))

(defn delete-indexes
  "Delete configured elastic indexes"
  []
  (let [response (client/request
                   {:method :post
                    :url es-prop/index-set-reset-url
                    :content-type :json
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))]
    (if (= 200 status)
      {:status status :body body}
      {:status status :errors-str (cheshire/generate-string (flatten (get body "errors")))})))

(defn create-index-set
  "Submit a request to create index-set"
  [index-set]
  (let [{:keys [status] :as result} (create-index-set-req index-set)]
    (when-not (= 201 status)
      (errors/internal-error! (format "Failed to create index-set: %s, errors: %s"
                                      (cheshire/generate-string index-set) (:errors-str result))))))

(defn es-concept-indices
  "Fetch index names for each concept type from index-set app"
  [index-set-id]
  (let [{:keys [status] :as result} (get-index-set index-set-id)
        fetched-index-set (:fetched-index-set result)]
    (when-not (= 200 status)
      (errors/internal-error! (format "index-set with id: %s not found. index-set app reported errors: %s"
                                      index-set-id (:errors-str result))))
    {:collection (first (vals (get-in fetched-index-set [:index-set :concepts :collection])))
     :granule (first (vals (get-in fetched-index-set [:index-set :concepts :granule])))}))

(defn es-concept-mapping-types
  "Fetch mapping types for each concept type from index-set app"
  [index-set-id]
  (let [{:keys [status] :as result} (get-index-set index-set-id)
        fetched-index-set (:fetched-index-set result)]
    (when-not (= 200 status)
      (errors/internal-error! (format "index-set with id: %s not found. index-set app reported errors: %s"
                                      index-set-id (:errors-str result))))
    {:collection (name (first (keys (get-in fetched-index-set [:index-set :collection :mapping]))))
     :granule (name (first (keys (get-in fetched-index-set [:index-set :granule :mapping]))))}))

(defn create-indexes
  "Create elastic index for each index name"
  []
  (let [index-set es-prop/index-set
        index-set-id (get-in index-set [:index-set :id])
        {:keys [status]} (get-index-set index-set-id)]
    (when (= 404 status)
      (create-index-set index-set))))











