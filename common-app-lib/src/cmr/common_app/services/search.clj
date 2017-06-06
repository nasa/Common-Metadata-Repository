(ns cmr.common-app.services.search
  "This contains common code for implementing search capabilities in a CMR application"
  (:require 
   [cmr.common.util :as u]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common-app.cache.cubby-cache :as cubby-cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common-app.services.search.query-validation :as qv]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   ;; Must be required to be available
   [cmr.common-app.services.search.validators.numeric-range]
   [cmr.common-app.services.search.validators.date-range]))

(def scroll-id-cache-key
  "Key for the scroll-id cache in the system cache map."
  "scroll-id-cache")

(defconfig scroll-id-cache-ttl
  "Time in milliseconds scroll-ids can stay in the cache before getting evicted."
  {:type Long
   ;; 24 hours
   :default (* 24 3600 1000)})

(defn create-scroll-id-cache
  "Returns a single-threaded cache wrapping a fallback cache that uses a consistent cache backed by
  cubby. This cache is used to store a map of cmr scroll-ids to ES scroll-ids in a consistent way 
  acrosss all instances of search."
  []
  (stl-cache/create-single-thread-lookup-cache
   (fallback-cache/create-fallback-cache
    (mem-cache/create-in-memory-cache :ttl {} {:time-to-live (scroll-id-cache-ttl)})
    (cubby-cache/create-cubby-cache))))

(defn validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [context query]
  (if-let [errors (seq (qv/validate query))]
    (errors/throw-service-errors :bad-request errors)
    query))

(defmulti search-results->response
  "Converts query search results into a string response."
  (fn [context query results]
    [(:concept-type query) (qm/base-result-format query)]))

(defmulti single-result->response
  "Returns a string representation of a single concept in the format
  specified in the query."
  (fn [context query results]
    [(:concept-type query) (qm/base-result-format query)]))

(defn find-concepts
  "Executes a search for concepts using the given query."
  [context concept-type query]
  (validate-query context query)
  (let [[query-execution-time results] (u/time-execution (qe/execute-query context query))
        [result-gen-time result-str] (u/time-execution
                                      (search-results->response
                                       context query (assoc results :took query-execution-time)))]
    (info "query-execution-time:" query-execution-time "result-gen-time:" result-gen-time)

    {:results result-str
     :hits (:hits results)
     :result-format (:result-format query)
     :scroll-id (:scroll-id results)}))