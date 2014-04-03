(ns cmr.indexer.data.elasticsearch-properties)

;; hard-code the following elasticsearch types for now
(def collection-setting { "index"
                         {"number_of_shards" 2
                          "number_of_replicas"  1
                          "refresh_interval" "1s"}})
(def collection-mapping
  {"collection" { "dynamic"  "strict"
                 "_source"  {"enabled" false}
                 "_all"     {"enabled" false}
                 "_id"      {"path" "concept-id"}
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

(def indexes [{:index-name "collections"
               :setting collection-setting
               :mapping collection-mapping}])
