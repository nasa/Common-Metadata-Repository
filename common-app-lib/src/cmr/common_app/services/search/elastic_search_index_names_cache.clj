(ns cmr.common-app.services.search.elastic-search-index-names-cache
  "Implements a cache to store Elasticsearch information used when searching
  elastic search for concepts."
  (:require
   [cmr.redis-utils.redis-hash-cache :as rhcache]))

(def index-names-cache-key
  "The name of the cache for caching index names. It will contain a map of concept type to a map of
   index names to the name of the index used in elasticsearch.

   Example:
   {:granule {:small_collections \"1_small_collections\"},
    :tag {:tags \"1_tags\"},
    :collection {:all-collection-revisions \"1_all_collection_revisions\",
                 :collections \"1_collections_v2\"}}"
  :index-names)

(defn create-index-cache
  "Used to create the cache that will be used for caching index names."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [index-names-cache-key]}))
