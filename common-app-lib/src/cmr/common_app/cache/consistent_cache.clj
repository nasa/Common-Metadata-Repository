(ns cmr.common-app.cache.consistent-cache
  "This defines an in memory cache that will be kept consistent with other instances of this cache.
  It achieves this by storing a hash code of the in memory values in another cache. Its expected that
  the hash code cache will be implemented to store the hashes in a single location (database, app,
  etc.) that can be accessed by any other instances of the consistent cache.

  ## What the Consistent Cache Guarantees

  If you have N consistent caches in different processes that share a common hash cache they will
  always return the same value as long as the value hasn't changed in any of them. Once one of them
  has an updated value the other caches will have that value \"invalidated\". Fetching the value from
  the other caches in that case will return nil or if a lookup function has been provided it will be
  used to retrieve the latest value.

  ## How it Works:

  Every time a value is stored in the cache it is put in the in memory cache and the hash code of that
  value is stored in the hash cache. Every time a value is retrieved the hash code is retrieved from
  the hash cache and compared to the value. If the hashes do not match it indicates that a value was
  modified in another cache so the current value should be considered out of date. Nil is returned
  in that case or if a lookup function is provided it is used to fetch the latest value.

  ## Benefits:

  The benefit of the consistent cache is that it has fast access to objects that are stored in memory
  but it is kept consistent with other processes also using the consistent cache. The only thing that
  needs to be transmitted is the hash code over the network. Invalidation of values in other caches
  is lazy. Nothing has to be sent to the other caches to notify them that their values are out of date.

  ## Downsides and Caveats:

  Every time you request or set a value in the consistent cache a request is made to the hash cache.
  Since the hash cache is most likely a remote cache this means a network request is made. It also
  means that the consistent cache will no longer work if the remote cache cannot be requested. Within
  the CAP theorem (Consistency, Availability, Partition Tolerance) consistency has been favored over
  availability.

  Hash codes are represented by an integer in Java which has 4,294,967,294 unique values. Two
  different values stored in the cache could possibly have the same hash code. The consistent cache
  would consider the two values as the same in that scenario and no invalidation would occur.

  Reading keys of the cache is an expensive operation. (See the comment there.) If you need fast access
  to the set of keys in the cache consider a different implementation."
  (:require [cmr.common.cache :as c]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common-app.cache.cubby-cache :as cubby-cache]
            [clojure.set :as set]))

(defrecord ConsistentMemoryCache
  [
   ;; The in memory cache holding values. Should implement CmrCache protocol.
   memory-cache

   ;; The cache that holds the hash values of items in the memory cache
   ;; Should implement CmrCache protocol.
   hash-cache
   ]

  c/CmrCache
  (get-keys
    [this]
    ;; This is an expensive operation. Any keys that we return here must have a value in memory cache
    ;; and a value in the hash cache. The hash code of the value in the memory cache must match
    ;; the value in the hash cache.
    (let [key-set (set/intersection (set (c/get-keys memory-cache))
                                    (set (c/get-keys hash-cache)))]
      (for [k key-set
            :when (= (hash (c/get-value memory-cache k)) (c/get-value hash-cache k))]
        k)))

  (get-value
    [this key]
    (when-let [mem-value (c/get-value memory-cache key)]
      (when (= (hash mem-value) (c/get-value hash-cache key))
        mem-value)))

  (get-value
    [this key lookup-fn]
    (or (c/get-value this key)
        (when-let [value (lookup-fn)]
          (c/set-value this key value)
          value)))

  (reset
    [this]
    (c/reset memory-cache)
    (c/reset hash-cache))

  (set-value
    [this key value]
    (c/set-value memory-cache key value)
    (c/set-value hash-cache key (hash value))))

(defn create-consistent-cache
  "Creates an instance of the consistent cache."
  ([]
   (create-consistent-cache
     (mem-cache/create-in-memory-cache)
     (cubby-cache/create-cubby-cache)))
  ([memory-cache hash-cache]
   (->ConsistentMemoryCache memory-cache hash-cache)))