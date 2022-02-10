(ns cmr.common.cache.deflating-cache
  "An implementation of the cache that uses a deflating function to store data
  in a delegate cache. An inflating function is used when retrieving data
  from the cache."
  (:require
   [cmr.common.cache :as c]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord DeflatingCache
  [
   ;; Delegate cache that stores values
   delegate-cache

   ;; Function for taking a value and inflating it from storage for retrieval
   inflate-fn

   ;; Function for taking a value and deflating it for storage
   deflate-fn]

  c/CmrCache
  (get-keys
    [this]
    (c/get-keys delegate-cache))

  (get-value
    [this key]
    (let [value (c/get-value delegate-cache key)]
      (when-not (nil? value)
        (inflate-fn value))))

  (get-value
    [this key lookup-fn]
    (let [value (c/get-value delegate-cache key
                             ;; If we lookup a value then it needs to be
                             ;; deflated for storage in the underlying cache.
                             (comp deflate-fn lookup-fn))]
      (when-not (nil? value)
        (inflate-fn value))))

  (reset
    [this]
    (c/reset delegate-cache))

  (set-value
    [this key value]
    (c/set-value delegate-cache
      key
      (when-not (nil? value) (deflate-fn value))))
  
  (cache-size
   [_]
   (c/cache-size delegate-cache)))

(record-pretty-printer/enable-record-pretty-printing DeflatingCache)

(defn create-deflating-cache
  "Create a deflating cache with the provided delegate-cache, inflate and
  deflate functions."
  [delegate-cache inflate-fn deflate-fn]
  (map->DeflatingCache {:delegate-cache delegate-cache
                        :inflate-fn inflate-fn
                        :deflate-fn deflate-fn}))
