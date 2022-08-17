(ns cmr.access-control.data.group-fetcher
  "Stores the group concept id to group legacy guid mapping in a consistent cache.
   We can cache Group's concept-id and legacy guid because they will never change.
   Group will get a new concept id if it is deleted and recreated;
   Group's legacy guid is not allowed to change when group is updated."
  (:require
   [cmr.access-control.services.group-service :as group-service]
   [cmr.common.cache :as c]
   [cmr.common.cache.in-memory-cache :as mem-cache]))

(def group-cache-key
  "The cache key to use when storing with caches in the system."
  :group-ids-guids)

(defn create-cache
  "Creates an instance of the cache."
  []
  (mem-cache/create-in-memory-cache))

(defn- cmr-group?
  "We detect if the group id passed is a CMR group and return true, false if otherwise."
  [group-id]
  (not (empty? (re-matches #"^AG\d+-[A-Za-z0-9_]+" group-id))))

(defn- get-group-legacy-guid
  "Returns the group legacy guid for the given group concept id by executing a search."
  [context group-id]
  (when (cmr-group? group-id)
    (:legacy-guid (group-service/get-group-by-concept-id context group-id))))

(defn group-concept-id->legacy-guid
  "Returns the group legacy guid for the given group concept id."
  [context group-id]
  (let [cache (c/context->cache context group-cache-key)]
    (c/get-value cache group-id (partial get-group-legacy-guid context group-id))))
