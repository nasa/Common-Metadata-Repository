(ns cmr.access-control.data.group-fetcher
  "Stores the group id to group legacy guid mapping in a consistent cache."
  (:require
   [cmr.access-control.services.group-service :as group-service]
   [cmr.common-app.services.search.parameter-validation :as parameter-validation]
   [cmr.common.cache :as c]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]))

(def group-cache-key
  "The cache key to use when storing with caches in the system."
  :groups)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache))

(defn- retrieve-group-id-to-legacy-guid-map
  [context]
  (let [groups (group-service/search-for-groups
                context {"page-size" parameter-validation/max-page-size})]
    (into {} (for [group (get-in groups [:results :items])
                   :let [legacy-guid (:legacy_guid group)]]
               [(:concept_id group) (if (nil? legacy-guid) "" legacy-guid)]))))

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
      (when (seq legacy-guid) legacy-guid)
      ;; legacy-guid does not exist in cache, we retrieve it and add it to the cache.
      ;; We can do this because once created, group's concept-id and legacy guid will never change.
      ;; Group will get a new concept id if it is deleted and recreated;
      ;; Group's legacy guid is not allowed to change when group is updated.
      (let [legacy-guid (:legacy_guid (group-service/get-group-by-concept-id context group-id))]
        (c/set-value cache group-cache-key
                     (assoc group-id-to-legacy-guid-map
                            group-id
                            (if (nil? legacy-guid) "" legacy-guid)))
        legacy-guid))))
