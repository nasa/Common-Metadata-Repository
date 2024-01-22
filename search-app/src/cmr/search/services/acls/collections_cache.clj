(ns cmr.search.services.acls.collections-cache
  "This is a cache of collection data for helping enforce granule acls in an efficient manner"
  (:require
    [cmr.common.hash-cache :as hash-cache]
    [cmr.common.log :as log :refer (debug info warn error)]
    [cmr.common-app.data.search.collection-for-gran-acls-caches :as coll-caches]))

(defn get-collection-for-gran-acls
  ([context coll-concept-id]
   "Gets a single collection from the cache by concept id. If collection is not found in cache, but exists in elastic, it will add it to the cache and will return the found collection."
   (let [coll-by-concept-id-cache (hash-cache/context->cache context coll-caches/coll-by-concept-id-cache-key)
         collection (hash-cache/get-value coll-by-concept-id-cache
                                          coll-caches/coll-by-concept-id-cache-key
                                          coll-concept-id)]
     (if (or (nil? collection) (empty? collection))
       (do
         (info (str "Collection with concept-id " coll-concept-id " not found in cache. Will update cache and try to find."))
         (coll-caches/time-strs->clj-times (coll-caches/set-caches context coll-concept-id)))
       (coll-caches/time-strs->clj-times collection)))))
