(ns cmr.common-app.services.search
  "This contains common code for implementing search capabilities in a CMR application"
  (:require
   [cmr.common.cache :as cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer (info)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.search.query-model :as qm]
   [cmr.common.util :as u]
   [cmr.elastic-utils.search.es-query-validation :as qv]
   [cmr.elastic-utils.search.query-execution :as qe]
   ;; Must be required to be available
   [cmr.elastic-utils.validators.date-range]
   [cmr.elastic-utils.validators.numeric-range]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-cache :as redis-cache]))

(def scroll-id-cache-key
  "Key for the scroll-id cache in the system cache map."
  :scroll-id-cache)

(def scroll-first-page-cache-key
  "Key for the first page of scroll results cache in the system cache map."
  :first-page-cache)

(defconfig scroll-id-cache-ttl
  "Time in seconds scroll-ids can stay in the cache before getting evicted."
  {:type Long
   ;; 24 hours
   :default (* 24 3600)})

(defconfig scroll-first-page-cache-ttl
  "Time in seconds the first page of results can stay in the cache before getting evicted."
  {:type Long
    ;; 15 minutes
   :default 900})

(defn create-scroll-id-cache
  "Returns a cache backed by Redis. This cache is used to store a map of cmr scroll-ids to ES scroll-ids
   in a consistent way acrosss all instances of search."
  []
  (redis-cache/create-redis-cache {:ttl (scroll-id-cache-ttl)
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn create-scroll-first-page-cache
  "Returns a cache backed by Redis. This cache is used to store a map of cmr scroll-ids to the first
   page of results in a consistent way acrosss all instances of search. This is used to support scrolling with
   sessions intitiated with a HEAD, GET, or POS request."
  []
  (redis-cache/create-redis-cache {:ttl (scroll-first-page-cache-ttl)
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [_context query]
  (if-let [errors (seq (qv/validate query))]
    (errors/throw-service-errors :bad-request errors)
    query))

(defmulti search-results->response
  "Converts query search results into a string response."
  (fn [_context query _results]
    [(:concept-type query) (qm/base-result-format query)]))

(defmulti single-result->response
  "Returns a string representation of a single concept in the format
  specified in the query."
  (fn [_context query _results]
    [(:concept-type query) (qm/base-result-format query)]))

(defn- add-scroll-results-to-cache
  "Adds the given search results (truncated to only the :hits, :timed-out, and :scroll-id keys)
  and result string to the cache using the scroll-id as the key"
  [context scroll-id results result-str]
  (when scroll-id
    (let [short-scroll-id (str "first-page-" (hash scroll-id))
          scroll-result-cache (cache/context->cache context scroll-first-page-cache-key)
          partial-results (select-keys results [:hits :timed-out])]
      (cache/set-value scroll-result-cache short-scroll-id [partial-results result-str]))))

(defn- pop-scroll-results-from-cache
  "Returns the first page of results from a scroll session (along with the original query
  execution time) from the cache using the scroll-id as a key. Clears the entry after
  reading it."
  [context scroll-id]
  (when scroll-id
    (let [short-scroll-id (str "first-page-" (hash scroll-id))]
      (when-let [result (-> context
                            (cache/context->cache scroll-first-page-cache-key)
                            (cache/get-value short-scroll-id))]
        ;; clear the cache entry
        (-> context
            (cache/context->cache scroll-first-page-cache-key)
            (cache/set-value short-scroll-id nil))
        result))))

(defn time-concept-search
  "Executes a search for concepts and returns the results while logging execution times."
  [context query]
  (let [[query-execution-time results] (u/time-execution (qe/execute-query context query))
        [result-gen-time result-str] (u/time-execution
                                      (search-results->response
                                       context query (assoc results :took query-execution-time)))]
    (info "query-execution-time:" query-execution-time "result-gen-time:" result-gen-time)
    [results result-str]))

(defn find-concepts
  "Executes a search for concepts using the given query."
  [context _concept-type query]
  (validate-query context query)
  ;; If the scroll-id is not nil, first look in the cache to see if there is a deferred result and
  ;; use that if so. If the scroll-id is not set and scroll is set to 'defer' then store the
  ;; search results in the cache and return an empty result (with appropriate headers). Otherwise
  ;; do normal query/scrolling without using the cache.
  (let [scroll-id (:scroll-id query)
        [results result-str] (or (pop-scroll-results-from-cache context scroll-id)
                                 (time-concept-search context query))]
    (if (and (not scroll-id) (= (:scroll query) "defer"))
      (let [new-scroll-id (:scroll-id results)
            empty-results (dissoc results :items :facets :aggregations)
            empty-result-str (search-results->response context query empty-results)]
        (add-scroll-results-to-cache context new-scroll-id results result-str)
        {:results empty-result-str
         :hits (:hits results)
         :timed-out (:timed-out results)
         :result-format (:result-format query)
         :scroll-id new-scroll-id})
      {:results result-str
       :hits (:hits results)
       :timed-out (:timed-out results)
       :result-format (:result-format query)
       :scroll-id (:scroll-id results)
       :search-after (:search-after results)})))
