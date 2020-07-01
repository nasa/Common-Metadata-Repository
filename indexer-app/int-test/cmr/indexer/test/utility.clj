(ns cmr.indexer.test.utility
  "Contains various utitiltiy methods to support integeration tests."
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.walk :as walk]
   [clojurewerkz.elastisch.rest :as esr]
   [cmr.elastic-utils.config :as es-config]
   [cmr.transmit.config :as transmit-config]))

(defn indexer-root-url
  []
  (format "%s:%s"  "http://localhost" (transmit-config/indexer-port)))

;; url applicable to create, get and delete index-set
(defn index-sets-url
  []
  (format "%s/index-sets" (indexer-root-url)))

(defn index-set-url
  [id]
  (format "%s/%s" (index-sets-url) id))

(defn rebalance-collection-url
  [index-set-id concept-id]
  (format "%s/rebalancing-collections/%s" (index-set-url index-set-id) concept-id))

(defn start-rebalance-collection-url
  [index-set-id concept-id]
  (str (rebalance-collection-url index-set-id concept-id) "/start"))

(defn finalize-rebalance-collection-url
  [index-set-id concept-id]
  (str (rebalance-collection-url index-set-id concept-id) "/finalize"))

(defn update-collection-rebalance-status-url
  [index-set-id concept-id]
  (str (rebalance-collection-url index-set-id concept-id) "/update-status"))

(defn reset-url
  []
  (format "%s/%s" (index-sets-url) "reset"))

(def cmr-concepts [:collection :granule])

;;; test data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sample-index-set-id 3)

(def sample-index-set
  {:index-set
   {:name "cmr-base-index-set"
    :id sample-index-set-id
    :create-reason "include message about reasons for creating this index set"
    :collection {:indexes
                 [{:name "C4-PROV2"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}
                  {:name "C6-PROV3"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}]
                 :mapping {:dynamic "strict",
                                        :_source {:enabled true},
                                        :properties {:concept-id  {:type "keyword" :norms false :index_options "docs"},
                                                     :entry-title {:type "keyword" :norms false :index_options "docs"}}}}
    :granule {:indexes
              [{:name "small_collections"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "C4-PROV3"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "C5-PROV5"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}]
              :individual-index-settings {:index {:number_of_shards 1,
                                                  :number_of_replicas 0,
                                                  :refresh_interval "10s"}}
              :mapping {:dynamic "strict",
                                  :_source {:enabled true},
                                  :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                               :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}}})

(def invalid-sample-index-set
  {:index-set
   {:name "cmr-base-index-set"
    :id sample-index-set-id
    :create-reason "include message about reasons for creating this index set"
    :collection {:indexes
                 [{:name "C4-collections"}
                  {:name "c6_Collections"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}]
                 :mapping {:collection {:dynamic "strict",
                                        :_source {:enabled true},
                                        :properties {:concept-id  {:type "keyword" :norms false :index_options "docs"},
                                                     :entry-title {:type "keyword" :norms false :index_options "docs"}}}}}
    :granule {:indexes
              [{:name "G2-PROV1"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "G4-Prov3"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "g5_prov5"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}]
              :mapping {:granule {:dynamic "strict",
                                  :_source {:enabled true},
                                  :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                               :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}}}})


(def index-set-w-invalid-idx-prop
  {:index-set
   {:name "cmr-base-index-set"
    :id sample-index-set-id
    :create-reason "include message about reasons for creating this index set"
    :collection {:indexes
                 [{:name "C4-collections"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}
                  {:name "c6_Collections"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}]
                 :mapping {:collection {:dynamic "strict",
                                        :_source {:enabled true},
                                        :properties {:concept-id  {:type "XXX" :index "not_analyzed" :norms false :index_options "docs"},
                                                     :entry-title {:type "keyword" :norms false :index_options "docs"}}}}}}})


;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn elastic-root
  []
  (format "http://%s:%s" (es-config/elastic-host) (es-config/elastic-port)))

(defn safe-decode
  "Safely decodes the response body as JSON. If there's an exception the body as a string is returned."
  [response]
  (try
    (cheshire/decode (:body response) true)
    (catch Exception _
      (:body response))))

(defn create-index-set
  "submit a request to index-set app to create indices"
  [idx-set]
  (let [response (client/request
                  {:method :post
                   :url (index-sets-url)
                   :body (cheshire.core/generate-string idx-set)
                   :content-type :json
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :accept :json
                   :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))

(defn mark-collection-as-rebalancing
  "Submits a request to mark a collection as rebalancing"
  [id concept-id]
  (let [response (client/request
                  {:method :post
                   :url (start-rebalance-collection-url id concept-id)
                   :query-params {:target "separate-index"}
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :accept :json
                   :throw-exceptions false})
        status (:status response)
        body (safe-decode response)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))

(defn finalize-rebalancing-collection
  "Submits a request to finalize a rebalancing collection"
  [id concept-id]
  (let [response (client/request
                  {:method :post
                   :url (finalize-rebalance-collection-url id concept-id)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :accept :json
                   :throw-exceptions false})
        status (:status response)
        body (safe-decode response)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))

(defn update-rebalancing-collection-status
  "Submits a request to update the collection rebalancing status"
  [id concept-id to-status]
  (let [response (client/request
                  {:method :post
                   :url (update-collection-rebalance-status-url id concept-id)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :query-params {:status to-status}
                   :accept :json
                   :throw-exceptions false})
        status (:status response)
        body (safe-decode response)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))

(defn delete-index-set
  "submit a request to index-set app to delete index-set"
  [id]
  (let [response (client/request
                  {:method :delete
                   :url (index-set-url id)
                   :accept :json
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))

(defn get-index-set
  "submit a request to index-set app to fetch an index-set assoc with an id"
  [id]
  (let [response (client/request
                  {:method :get
                   :url (index-set-url id)
                   :accept :json
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))

(defn get-index-sets
  "submit a request to index-set app to fetch all index-sets"
  []
  (let [response (client/request
                  {:method :get
                   :url (index-sets-url)
                   :accept :json
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))

(defn reset
  "test deletion of indices and index-sets"
  []
  (let [response (client/request
                  {:method :post
                   :url (reset-url)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :accept :json})
        status (:status response)]
    {:status status :response response}))

(defn list-es-indices
  "List indices present in 'get index-sets' response"
  [index-sets]
  (apply concat
         (for [concept cmr-concepts idx-set index-sets]
           (vals (get-in idx-set [:concepts concept])))))


(def elastic-connection (atom nil))

(defn reset-fixture [f]
  (reset)
  (reset! elastic-connection (esr/connect (elastic-root)))
  (f)
  (reset))
