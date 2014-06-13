(ns cmr.index-set.config.elasticsearch-config)

;; index name and config for storing index-set requests
;; index the request after creating all of the requested indices successfully
;; foot print of this index will remain small
(def idx-cfg-for-index-sets
  {:index-name "index-sets"
   :settings {"index" {"number_of_shards" 1
                       "number_of_replicas"  1
                       "refresh_interval" "30s"}}
   :mapping {"set" { "dynamic"  "strict"
                    "_source"  {"enabled" true}
                    "_all"     {"enabled" false}
                    "_id"      {"path" "index-set-id"}
                    :properties {:index-set-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                 :index-set-name {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                 :index-set-name.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                                 :index-set-request {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}})

