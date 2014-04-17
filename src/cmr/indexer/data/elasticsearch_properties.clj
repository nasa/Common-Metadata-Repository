(ns cmr.indexer.data.elasticsearch-properties)

;;; index-set app enpoint
(def index-set-app-port 3005)

(def index-set-root-url
  (format "%s:%s"  "http://localhost" index-set-app-port))

;; url applicable to create, get and delete index-sets
(def index-set-url
  (format "%s/%s" index-set-root-url "index-sets"))

;; reset indices for dev purposes
(def index-set-reset-url
  (format "%s/%s" index-set-root-url "reset"))

(def index-set
  {:index-set {:name "cmr-base-index-set"
               :id 1
               :create-reason "indexer app requires this index set"
               :collection {:index-names ["collections"]
                            :settings {:index {:number_of_shards 2,
                                               :number_of_replicas 1,
                                               :refresh_interval "1s"}}
                            :mapping {:collection {:dynamic "strict",
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
                                                                :end-date    {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"}}}}}
               :granule {:index-names ["granules"]
                         :settings {:index {:number_of_shards 2,
                                            :number_of_replicas 1,
                                            :refresh_interval "1s"}}
                         :mapping {:small_collections {:dynamic "strict",
                                                       :_source { "enabled" false},
                                                       :_all {"enabled" false},
                                                       :_id  {:path "concept-id"},
                                                       :properties {:concept-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                                    :collection-concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                                    :provider-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                                    :provider-id.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                                                                    :granule-ur  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                                                    :granule-ur.lowercase  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}}}}}}})
