(ns cmr.access-control.data.group-fetcher
  "Stores the group id to group legacy guid mapping in a consistent cache."
  (:require
   [cmr.access-control.services.group-service :as group-service]
   [cmr.common-app.services.search.parameter-validation :as parameter-validation]
   [cmr.common.cache :as c]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]))

(def group-cache-key
  "The cache key to use when storing with caches in the system."
  :group-ids-guids)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache))

(defn- legacy-guid->cache-value
  "Returns the value that is stored in cache for the given legacy guid.
   If legacy guid is nil, we store an empty string as the value for legacy guid.
   This way, we can distinguish the case when a group id does not have a legacy guid
   from a group id is not in the cache in one map lookup."
  [legacy-guid]
  (if (nil? legacy-guid) "" legacy-guid))

(defn- cache-value->legacy-guid
  "Returns the legacy guid from the stored cache value."
  [cache-value]
  ;; retuns nil if cached value is an empty string
  (when (seq cache-value) cache-value))

(defn- retrieve-group-id-to-legacy-guid-map
  [context]
  (let [groups (group-service/search-for-groups
                context {"page-size" parameter-validation/max-page-size})]
    (into {} (for [group (get-in groups [:results :items])
                   :let [legacy-guid (:legacy_guid group)]]
               [(:concept_id group) (legacy-guid->cache-value legacy-guid)]))))

(defn refresh-cache
  "Refreshes the group id to group legacy guid map in the cache."
  [context]
  (let [cache (c/context->cache context group-cache-key)]
    (c/set-value cache group-cache-key
                 (retrieve-group-id-to-legacy-guid-map context))))

(defn group-id->legacy-guid
  "Returns the group legacy guid for the given group concept id."
  [context group-id]
  (let [cache (c/context->cache context group-cache-key)
        group-id-to-legacy-guid-map (c/get-value
                                     cache group-cache-key
                                     ;; This retrieve is added to make dev repl work
                                     (partial retrieve-group-id-to-legacy-guid-map context))]
    (if-let [legacy-guid (get group-id-to-legacy-guid-map group-id)]
      (cache-value->legacy-guid legacy-guid)
      ;; legacy-guid does not exist in cache, we retrieve it and add it to the cache.
      ;; We can do this because once created, group's concept-id and legacy guid will never change.
      ;; Group will get a new concept id if it is deleted and recreated;
      ;; Group's legacy guid is not allowed to change when group is updated.
      (let [legacy-guid (:legacy_guid (group-service/get-group-by-concept-id context group-id))]
        (c/set-value cache group-cache-key
                     (assoc group-id-to-legacy-guid-map
                            group-id
                            (legacy-guid->cache-value legacy-guid)))
        legacy-guid))))
