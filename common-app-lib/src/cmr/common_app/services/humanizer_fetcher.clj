(ns cmr.common-app.services.humanizer-fetcher
  "Stores the latest humanizer json in a consistent cache."
  (require [cmr.common.jobs :refer [def-stateful-job]]
           ;; cache dependencies
           [cmr.common.cache :as c]
           [cmr.common-app.cache.consistent-cache :as consistent-cache]
           [cmr.common.cache.single-thread-lookup-cache :as stl-cache]

           [cmr.transmit.search :as search]))

(def humanizer-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache
    (consistent-cache/create-consistent-cache)))

(defmulti retrieve-humanizer
  "Returns the humanizer json by retrieving it from metadata-db.
  indexer-app and search-app make different calls to retrieve the humanizer json.
  indexer-app uses the default implementation."
  (fn [context]
    (:app context)))

(defmethod retrieve-humanizer :default
  [context]
  (search/get-humanizer context))

(defn refresh-cache
  "Refreshes the humanizer in the cache."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)]
    (c/set-value cache humanizer-cache-key
                 (retrieve-humanizer context))))

(defn get-humanizer
  "Returns the humanizer."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)]
    (c/get-value cache
                 humanizer-cache-key
                 #(retrieve-humanizer context))))


