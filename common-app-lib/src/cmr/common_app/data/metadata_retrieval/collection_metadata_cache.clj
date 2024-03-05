(ns cmr.common-app.data.metadata-retrieval.collection-metadata-cache
  "Defines common functions and defs a cache for catalog item metadata.
  It currently only stores collections.
  The metadata cache contains data like the following:

  concept-id -> revision-format-map
                 * concept-id
                 * revision-id
                 * native-format - A key or map of format and version identifying the native format
                 * various format keys each mapped to compressed metadata."
  (:require
   [cmr.common-app.data.metadata-retrieval.revision-format-map :as crfm]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as q]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.date-time-parser :as parser]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.util :as u]
   [cmr.common.xml :as cx]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as rhcache]))

(def cache-key
  "Identifies the key used when the cache is stored in the system."
  :collection-metadata-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [cache-key]
                                    :read-connection (redis-config/redis-collection-metadata-cache-read-conn-opts)
                                    :primary-connection (redis-config/redis-collection-metadata-cache-conn-opts)}))

(def incremental-since-refresh-date-key
  "Identifies the field used in the defined cache-key to get the date represented
  as a string to get collections from ES the last time they where fetched."
  "incremental-since-refresh-date")

(def collection-metadata-cache-fields-key
  "Identifies a list of concept ids, to speed up getting collection metadata cache data
  for the humanizer report."
  "concept-id-keys")

(defn context->metadata-db-context
  "Converts the context into one that can be used to invoke the metadata-db services."
  [context]
  (assoc context :system (get-in context [:system :embedded-systems :metadata-db])))

(defn fetch-collections-from-elastic
  "Executes a query that will fetch all of the collection information needed for caching."
  [context]
  (let [query (q/query {:concept-type :collection
                        :condition q/match-all
                        :skip-acls? true
                        :page-size :unlimited
                        :result-format :query-specified
                        :result-fields [:concept-id :revision-id]})]
    (mapv #(vector (:concept-id %) (:revision-id %))
          (:items (qe/execute-query context query)))))

(defn- data-range-condition
  "Parses the date string into a cmr.common.joda-time and puts it into an elastic search
   query condition so that the date can be used in an elastic search query."
  [date]
  (q/map->DateRangeCondition
   {:field :revision-date
    :start-date (parser/parse-datetime (str date))
    :end-date nil}))

(defn fetch-updated-collections-from-elastic
  "Executes a query that will fetch all of the updated collection information needed for caching."
  [context]
  (let [cache (hash-cache/context->cache context cache-key)
        incremental-since-refresh-date (hash-cache/get-value cache
                                                             cache-key
                                                             incremental-since-refresh-date-key)
        query (q/query {:concept-type :collection
                        :condition (data-range-condition (or incremental-since-refresh-date "1600-01-01T00:00:00"))
                        :skip-acls? true
                        :page-size :unlimited
                        :result-format :query-specified
                        :result-fields [:concept-id :revision-id]})]
    (mapv #(vector (:concept-id %) (:revision-id %))
          (:items (qe/execute-query context query)))))

(defn concepts-without-xml-processing-inst
  "Removes XML processing instructions from a list of concepts."
  [concepts]
  (u/fast-map
   #(update % :metadata cx/remove-xml-processing-instructions)
   concepts))

(defconfig update-collection-metadata-cache-interval
  "The number of seconds between refreshes of the collection metadata cache"
  {:default (* 3600 8)
   :type Long})

(defn prettify-cache
  "Returns a simplified version of the cache to help with debugging cache problems."
  [cache]
  (let [cache-map (hash-cache/get-map cache cache-key)
        incremental-since-refresh-date (get cache-map incremental-since-refresh-date-key)
        revision-format-map (dissoc cache-map incremental-since-refresh-date-key collection-metadata-cache-fields-key)]
    (assoc (u/map-values crfm/prettify revision-format-map)
           incremental-since-refresh-date-key
           incremental-since-refresh-date)))
