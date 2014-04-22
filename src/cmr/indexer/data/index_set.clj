(ns cmr.indexer.data.index-set
  (:require [cmr.common.lifecycle :as lifecycle]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.system-trace.core :refer [deftracefn]]))

;;; index-set app enpoint
(def index-set-app-port 3005)

(def index-set-root-url
  (format "%s:%s"  "http://localhost" index-set-app-port))

;; url applicable to create, get and delete index-sets
(def index-set-url
  (format "%s/%s" index-set-root-url "index-sets"))

;; url applicable to create, get and delete index-sets
(def index-set-es-cfg-url
  (format "%s/%s" index-set-root-url "elastic-config"))

;; reset indices for dev purposes
(def index-set-reset-url
  (format "%s/%s" index-set-root-url "reset"))

(def collection-setting {:index
                         {:number_of_shards 2,
                          :number_of_replicas 1,
                          :refresh_interval "1s"}})
(def collection-mapping
  {:collection {:dynamic "strict",
                :_source {:enabled false},
                :_all {:enabled false},
                :_id   {:path "concept-id"},
                :properties {:concept-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                             :entry-title {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                             :entry-title.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                             :provider-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                             :provider-id.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                             :short-name  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                             :short-name.lowercase  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                             :version-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                             :version-id.lowercase  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                             :start-date  {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"}
                             :end-date    {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"}}}})

(def granule-setting {:index {:number_of_shards 2,
                              :number_of_replicas 1,
                              :refresh_interval "1s"}})

(def granule-mapping {:small_collections {:dynamic "strict",
                                          :_source { "enabled" false},
                                          :_all {"enabled" false},
                                          :_id  {:path "concept-id"},
                                          :properties {:concept-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                       :collection-concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                       :provider-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                       :provider-id.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                                                       :granule-ur  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                       :granule-ur.lowercase  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}}}})

(def index-set
  {:index-set {:name "cmr-base-index-set"
               :id 1
               :create-reason "indexer app requires this index set"
               :collection {:index-names ["collections"]
                            :settings collection-setting
                            :mapping collection-mapping}
               :granule {:index-names ["granules"]
                         :settings granule-setting
                         :mapping granule-mapping}}})


(defn  get-index-set
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  [id]
  (let [response (client/request
                   {:method :get
                    :url (format "%s/%s" index-set-url (str id))
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    (case status
      404 nil
      200 body
      (errors/internal-error! (format "Unexpected error fetching index-set with id: %s,
                                      Index set app reported status: %s, error: %s"
                                      id status (pr-str (flatten (:errors body))))))))

(defn  get-elastic-config
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  []
  (let [response (client/request
                   {:method :get
                    :url index-set-es-cfg-url
                    :accept :json})]
    (cheshire/decode (:body response) true)))


(defn reset
  "Reset configured elastic indexes"
  []
  (client/request
    {:method :post
     :url index-set-reset-url
     :content-type :json
     :accept :json}))

(defn create-index-set
  "Submit a request to create index-set"
  [index-set]
  (let [response (client/request
                   {:method :post
                    :url index-set-url
                    :body (cheshire/generate-string index-set)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false})
        status (:status response)]
    (when-not (= 201 status)
      (errors/internal-error! (format "Failed to create index-set: %s, errors: %s"
                                      (cheshire/generate-string index-set)
                                      (:body response))))))

(defn get-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  ([]
   (let [index-set-id (get-in index-set [:index-set :id])]
     (get-concept-type-index-names index-set-id)))
  ([index-set-id]
   (let [fetched-index-set (get-index-set index-set-id)]
     (into {} (map (fn [[k v]] [k (first (vals v))])
                   (get-in fetched-index-set [:index-set :concepts]))))))


(defn get-concept-mapping-types
  "Fetch mapping types for each concept type from index-set app"
  ([]
   (let [index-set-id (get-in index-set [:index-set :id])]
     (get-concept-mapping-types index-set-id)))
  ([index-set-id]
   (let [fetched-index-set (get-index-set index-set-id)]
     {:collection (name (first (keys (get-in fetched-index-set [:index-set :collection :mapping]))))
      :granule (name (first (keys (get-in fetched-index-set [:index-set :granule :mapping]))))})))



