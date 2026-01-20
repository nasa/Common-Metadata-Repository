(ns cmr.indexer.test.utility
  "Contains various utitiltiy methods to support integeration tests."
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.test :refer :all]
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
                 [{:name "collections-v2"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}
                  {:name "all-collection-revisions"
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

(def expected-orig-index-set
  "Expected orig index set that is returned from calling get index-set in the update-index-sets-test"
  {:index-set
   {:name "cmr-base-index-set",
    :id 3,
    :create-reason "include message about reasons for creating this index set"
    :granule {:indexes [{:name "small_collections",
                         :settings {:index {:number_of_shards 1, :number_of_replicas 0, :refresh_interval "10s"}}}
                        {:name "C4-PROV3",
                         :settings {:index {:number_of_shards 1, :number_of_replicas 0, :refresh_interval "10s"}}}
                        {:name "C5-PROV5",
                         :settings {:index {:number_of_shards 1, :number_of_replicas 0, :refresh_interval "10s"}}}]
              :individual-index-settings {:index {:number_of_shards 1, :number_of_replicas 0, :refresh_interval "10s"}}
              :mapping {:dynamic "strict",
                        :_source {:enabled true},
                        :properties {:concept-id {:type "keyword", :norms false, :index_options "docs"},
                                     :collection-concept-id {:type "keyword", :norms false, :index_options "docs"}}}},
    :concepts {:generic-order-option {},
               :service {},
               :generic-tool-draft {},
               :variable {},
               :generic-grid-draft {},
               :generic-service-draft {},
               :deleted-granule {},
               :tool {},
               :generic-visualization {},
               :generic-citation {},
               :generic-collection-draft {},
               :generic-visualization-draft {},
               :granule {:small_collections "3_small_collections",
                         :C4-PROV3 "3_c4_prov3",
                         :C5-PROV5 "3_c5_prov5"},
               :generic-order-option-draft {},
               :generic-data-quality-summary-draft {},
               :generic-variable-draft {},
               :generic-citation-draft {},
               :autocomplete {},
               :tag {},
               :generic-grid {},
               :generic-data-quality-summary {},
               :collection {:collections-v2 "3_collections_v2",
                            :all-collection-revisions "3_all_collection_revisions"},
               :subscription {}},
    :collection {:indexes [{:name "collections-v2",
                            :settings {:index {:number_of_shards 1, :number_of_replicas 0, :refresh_interval "20s"}}}
                           {:name "all-collection-revisions",
                            :settings {:index {:number_of_shards 1, :number_of_replicas 0, :refresh_interval "20s"}}}]
                 :mapping {:dynamic "strict",
                           :_source {:enabled true},
                           :properties {:concept-id {:type "keyword", :norms false, :index_options "docs"},
                                        :entry-title {:type "keyword", :norms false, :index_options "docs"}}}}}})

(def sample-index-set-updated
  {:index-set
   {:name "cmr-base-index-set-updated"
    :id sample-index-set-id
    :create-reason "updated index set from sample index"
    ;; random concept type is created
    :random {:indexes
             [{:name "RAND1-PROV0"
               :settings {:index {:number_of_shards 1,
                                  :number_of_replicas 0,
                                  :refresh_interval "20s"}}}]
             :mapping {:dynamic "strict",
                       :_source {:enabled true},
                       :properties {:concept-id  {:type "keyword" :norms false :index_options "docs"},
                                    :entry-title {:type "keyword" :norms false :index_options "docs"}}}}
    ;; removed all existing collection indexes and created a new one
    :collection {:indexes
                 [{:name "COLL2-PROV1"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}]
                 :mapping {:dynamic "strict",
                           :_source {:enabled true},
                           :properties {:concept-id  {:type "keyword" :norms false :index_options "docs"},
                                        :entry-title {:type "keyword" :norms false :index_options "docs"}}}}
    ;; removed C4 and C5 index and added C6 and C7
    :granule {:indexes
              [{:name "small_collections"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "C6-PROV3"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "C7-PROV4"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}]
              :individual-index-settings {:index {:number_of_shards 1,
                                                  :number_of_replicas 0,
                                                  :refresh_interval "10s"}}
              :mapping {:dynamic "strict",
                        :_source {:enabled true},
                        :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                     :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    ;; added a generic index
    :generic-citation {:indexes
                       [{
                         :name "generic-citation"
                         :settings {:index {:number_of_shards 3,
                                            :number_of_replicas 1,
                                            :refresh_interval "1s"}}
                         }]
                       :mapping {:dynamic "strict",
                                 :_source {:enabled true},
                                 :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                              :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    ;; added service concept index
    :service {:indexes
              [{
                :name "services"
                :settings {:index {:number_of_shards 5,
                                    :number_of_replicas 1,
                                    :max_result_window 1000000,
                                    :refresh_interval "1s"}}}]
              :mapping {:dynamic "strict",
                        :_source {:enabled true},
                        :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                     :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    ;; added concepts list
    :concepts {
               :generic-citation {:generic-citation "3_generic_citation"}
               :granule {:small_collections "3_small_collections"}
               :collection {:collections-v2 "3_collections_v2"}
               :service {:services "3_services"}}}})

(def expected-sample-index-set-after-update
  {:index-set
   {:name "cmr-base-index-set-updated"
    :id sample-index-set-id
    :create-reason "updated index set from sample index"
    ;; random concept type is created
    :random {:indexes
             [{:name "RAND1-PROV0"
               :settings {:index {:number_of_shards 1,
                                  :number_of_replicas 0,
                                  :refresh_interval "20s"}}}]
             :mapping {:dynamic "strict",
                       :_source {:enabled true},
                       :properties {:concept-id  {:type "keyword" :norms false :index_options "docs"},
                                    :entry-title {:type "keyword" :norms false :index_options "docs"}}}}
    ;; removed all existing collection indexes and created a new one
    :collection {:indexes
                 [{:name "COLL2-PROV1"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}]
                 :mapping {:dynamic "strict",
                           :_source {:enabled true},
                           :properties {:concept-id  {:type "keyword" :norms false :index_options "docs"},
                                        :entry-title {:type "keyword" :norms false :index_options "docs"}}}}
    ;; removed C4 and C5 index and added C6 and C7
    :granule {:indexes
              [{:name "small_collections"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "C6-PROV3"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "C7-PROV4"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}]
              :individual-index-settings {:index {:number_of_shards 1,
                                                  :number_of_replicas 0,
                                                  :refresh_interval "10s"}}
              :mapping {:dynamic "strict",
                        :_source {:enabled true},
                        :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                     :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    ;; added a generic index
    :generic-citation {:indexes
                       [{
                         :name "generic-citation"
                         :settings {:index {:number_of_shards 3,
                                            :number_of_replicas 1,
                                            :refresh_interval "1s"}}
                         }]
                       :mapping {:dynamic "strict",
                                 :_source {:enabled true},
                                 :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                              :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    ;; added service concept index
    :service {:indexes
              [{
                :name "services"
                :settings {:index {:number_of_shards 5,
                                   :number_of_replicas 1,
                                   :max_result_window 1000000,
                                   :refresh_interval "1s"}}}]
              :mapping {:dynamic "strict",
                        :_source {:enabled true},
                        :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                     :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    ;; added concepts list
    :concepts {
               :generic-citation {:generic-citation "3_generic_citation"}
               :granule {:small_collections "3_small_collections"
                         :C6-PROV3 "3_c6_prov3"
                         :C7-PROV4 "3_c7_prov4"}
               :collection {:COLL2-PROV1 "3_coll2_prov1"}
               :service {:services "3_services"}
               :autocomplete {},
               :deleted-granule {},
               :generic-citation-draft {},
               :generic-collection-draft {},
               :generic-data-quality-summary {},
               :generic-data-quality-summary-draft {},
               :generic-grid {},
               :generic-grid-draft {},
               :generic-order-option {},
               :generic-order-option-draft {},
               :generic-service-draft {},
               :generic-tool-draft {},
               :generic-variable-draft {},
               :generic-visualization {},
               :generic-visualization-draft {},
               :subscription {},
               :tag {},
               :tool {},
               :variable {}}}})

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


(def expected-empty-index-set
  "Given an empty index set this is the expected created index-set. This is used for the update-index-sets-test."
  {:index-set {:name "test-index-set",
               :id 3,
               :concepts {
                          :generic-order-option {},
                          :service {},
                          :generic-tool-draft {},
                          :variable {},
                          :generic-grid-draft {},
                          :generic-service-draft {},
                          :deleted-granule {},
                          :tool {},
                          :generic-visualization {},
                          :generic-citation {},
                          :generic-collection-draft {},
                          :generic-visualization-draft {},
                          :granule {},
                          :generic-order-option-draft {},
                          :generic-data-quality-summary-draft {},
                          :generic-variable-draft {},
                          :generic-citation-draft {},
                          :autocomplete {},
                          :tag {},
                          :generic-grid {},
                          :generic-data-quality-summary {},
                          :collection {},
                          :subscription {}},
               :create-reason nil}})
;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn gran-elastic-root
  []
  (format "http://%s:%s" (es-config/gran-elastic-host) (es-config/gran-elastic-port)))

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

(defn update-index-set
  "Submit a request to index-set app to create or update indices"
  [idx-set id]
  (let [response (client/request
                   {:method :put
                    :url (index-set-url id)
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
  ([id]
  (let [response (client/request
                  {:method :get
                   :url (index-set-url id)
                   :accept :json
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))
  ([id es-cluster-name]
   (let [response (client/request
                    {:method :get
                     :url (format "%s/cluster/%s/%s" (index-sets-url) es-cluster-name id)
                     :accept :json
                     :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                     :throw-exceptions false})
         status (:status response)
         body (cheshire/decode (:body response) true)]
     {:status status :errors (:errors body) :response (assoc response :body body)})))


(defn get-index-sets
  "submit a request to index-set app to fetch all index-sets"
  ([]
  (let [response (client/request
                  {:method :get
                   :url (index-sets-url)
                   :accept :json
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    {:status status :errors (:errors body) :response (assoc response :body body)}))
  ([es-cluster-name]
   (let [response (client/request
                    {:method :get
                     :url (format "%s/index-sets/cluster/%s" (indexer-root-url) es-cluster-name)
                     :accept :json
                     :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                     :throw-exceptions false})
         status (:status response)
         body (cheshire/decode (:body response) true)]
     {:status status :errors (:errors body) :response (assoc response :body body)})))

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


(def gran-elastic-connection (atom nil))
(def elastic-connection (atom nil))

(defn reset-fixture [f]
  (reset)
  (reset! gran-elastic-connection (esr/connect (gran-elastic-root)))
  (reset! elastic-connection (esr/connect (elastic-root)))
  (f)
  (reset))
