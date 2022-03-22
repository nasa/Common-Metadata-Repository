(ns cmr.search.services.humanizers.humanizer-range-facet-service
  "Provides functions for working with range facet humanizers"
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.log :as log :refer [warn]]
   [cmr.common.util :refer [convert-to-meters]]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.search.services.humanizers.humanizer-messages :as msg]
   [cmr.search.services.humanizers.humanizer-service :as hs]
   [cmr.search.services.parameters.converters.range-facet :as range-facet]
   [cmr.transmit.cache.consistent-cache :as consistent-cache]))

(def range-facet-cache-key
  "The key used to store the humanizer facet range cache in the system cache map."
  :humanizer-range-facet-cache)

(def range-facet-data-key
  "The key used when setting the cache value of the report data."
  :range-facets)

(defn create-range-facet-cache
  "This function creates the composite cache that is used for caching the
  humanizer range facets. With the given composition we get the following features:
  * A Redis cache that holds the generated report;
  * A fast access in-memory cache that sits on top of Redis, providing
    quick local results after the first call to Redis; this cache is kept
    consistent across all instances of CMR, so no matter which host the LB
    serves, all the content is the same;
  * A single-threaded cache that circumvents potential race conditions
    between HTTP requests for a report and Quartz cluster jobs that save
    report data."
  []
  (stl-cache/create-single-thread-lookup-cache
   (fallback-cache/create-fallback-cache
    (consistent-cache/create-consistent-cache)
    (redis-cache/create-redis-cache))))

(def addition-factor
  "Range aggregation does not include the :to number in the facets so we add a very small number
   in meters so that the :to value is included, but doesn't affect the outcome of the result
   besides that."
  0.0001)

(defn create-facet-range
  "This function parses a humanizer range facet map that contains range facet values of
   '1 meter & above'
   '1 meter +'
   '1+ meter'
   '0 to meter' or
   '0 meter to 1 meter'
   and converts it to to a range facet. If the units are not meters then this function
   will convert the values to meters. This function splits the string into 2 part by the dash (to)
   Then it parses each part and merges the results together. Range aggregation does not include the
   :to number in the facets so we add a very small number in meters so that the :to value is
   included, but doesn't affect the outcome of the result besides that."
  [humanizer]
  (let [range-string (:source_value humanizer)
        values (range-facet/parse-range range-string)]
    (merge
     {:key range-string
      :from (get values 0)
      :to (+ (get values 1) addition-factor)})))

(defn get-humanizers
  "This function tries to get humanizers from the database and gets the horizontal_range_facets
   humanizers if it can. If if can't then it reads a default set."
  [context]
  (let [humanizers (try
                     (hs/get-humanizers context)
                     (catch Exception e
                       (warn (.getMessage e) msg/trouble-getting-humanizers)
                       nil))
        range-humanizers (for [humanizer humanizers
                               :when (= "horizontal_range_facets" (:type humanizer))]
                           humanizer)]
    (if (seq range-humanizers)
      range-humanizers
      (into []
        (json/decode (slurp (io/resource "default_range_facet_humanizers.json")) true)))))

(defn create-range-facets-from-humanizers
  "This function creates range facet data from the humanizers. Use the humanizers that are passed in
   for testing purposes otherwise get them from the database."
  ([context]
   (create-range-facets-from-humanizers context nil))
  ([context humanizers]
   (let [humanizers (if humanizers
                      humanizers
                      (get-humanizers context))]
     (into []
           (map create-facet-range humanizers)))))

(defn store-range-facets
  "Stores the passed in range facets into the range facet cache. All searches will be using range
   facets initially and caching range facets prevents every inital search call from parsing the
   range facet humanizers, which keeps the search service fast."
  [context range-facets]
  (when range-facets
    (when-let [cache (cache/context->cache context range-facet-cache-key)]
      (cache/set-value cache range-facet-data-key range-facets))))

(defn get-and-store-range-facets
  "Gets and stores into the range facet cache the range facets from the humanizers. All searches
   will be using range facets initially and caching range facets prevents every inital search call
   from parsing the range facet humanizers, which keeps the search service fast."
  [context]
  (let [range-facets (create-range-facets-from-humanizers context)]
    (store-range-facets context range-facets)
    range-facets))

(defn get-range-facets
  "Gets the range facets from the range facet cache if they exist. Otherwise create the range facets
   and store them."
  [context]
  (let [cache (cache/context->cache context range-facet-cache-key)
        value (when cache
                (cache/get-value cache range-facet-data-key))]
    (if (seq value)
      value
      (get-and-store-range-facets context))))
