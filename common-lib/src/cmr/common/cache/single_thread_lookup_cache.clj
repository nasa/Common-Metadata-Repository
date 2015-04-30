(ns cmr.common.cache.single-thread-lookup-cache
  "Defines a cache that serializes all calls to execute the lookup function on a single thread. It
  should be used when the lookup function is very expensive and you want to minimize the number of
  times it's called.

  When the value is already cached the request for a value is very fast. When the value is not
  cached a request is queued to fetch the value. A separate thread processes these requests off a
  core.async channel. The processing this way forces the serialization of cache lookups during a
  cache miss. Earlier cache requests will likely populate the cache with a single request using
  the lookup function. This will allow the subsequent queued requests to avoid calling the lookup
  function.

  This cache implements the cmr.common.lifecycle/Lifecycle protocol and must be started and stopped
  because it has a separate running thread."
  (:require [cmr.common.cache :as c]
            [clojure.core.async :as async]
            [cmr.common.util :as u]
            [cmr.common.lifecycle :as l]
            [cmr.common.cache.in-memory-cache :as mc]))

(defn- create-lookup-process-thread
  "Starts a thread that will listen on the lookup-request-channel for requests to fetch values
  from the cache. It will fetch the value from the cache and put it back on the response-channel
  in the message."
  [{:keys [delegate-cache lookup-request-channel]}]
  (async/thread
    ;; Attempt to read messages from the lookup request channel until it's closed.
    (u/while-let
      [{:keys [key lookup-fn response-channel]} (async/<!! lookup-request-channel)]
      ;; Get the requested value and put it back on the response channel.
      ;; The delegate cache will determine if calling the lookup function is necessary.
      (async/>!! response-channel (c/get-value delegate-cache key lookup-fn)))))


(defrecord SingleThreadLookupCache
  [
   ;; The underlying cache this will delegate to.
   delegate-cache

   ;; A core.async channel containing requests to lookup a value that was initially missed on the
   ;; cache. Each message will contain a map with the :lookup-fn, :key, and :response-channel
   lookup-request-channel

   ;; The channel returned when creating the single thread processes messages off the lookup-request-channel.
   lookup-process-thread-channel
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  c/CmrCache
  (get-keys
    [this]
    (c/get-keys delegate-cache))

  (get-value
    [this key]
    (c/get-value delegate-cache key))

  (get-value
    [this key lookup-fn]
    (or
      ;; Get the value out of the cache if it's available
      (c/get-value this key)
      ;; Or queue a request to get the value and wait for a response
      (let [response-channel (async/chan)]
        (async/>!! lookup-request-channel {:key key
                                     :lookup-fn lookup-fn
                                     :response-channel response-channel})
        (async/<!! response-channel))))

  (reset
    [this]
    (c/reset delegate-cache))

  (set-value
    [this key value]
    (c/set-value delegate-cache key value))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  l/Lifecycle
  (start
    [this system]
    (if lookup-request-channel
      ;; already running
      this
      ;; Not started yet
      (as-> this the-cache
            (assoc the-cache :lookup-request-channel (async/chan))
            (assoc the-cache :lookup-process-thread-channel
                   (create-lookup-process-thread the-cache)))))

  (stop
    [this system]
    (if lookup-request-channel
      ;; Running
      (-> this
          (update-in [:lookup-request-channel] async/close!)
          ;; Wait for the thread to finish and then it should be closed
          (update-in [:lookup-process-thread-channel] async/<!!))
      ;; Not running
      this)))

(defn create-single-thread-lookup-cache
  "Creates an instance of the single thread lookup cache that will delegate to the specified cache.
  Defaults to using an in-memory cache."
  ([]
   (create-single-thread-lookup-cache (mc/create-in-memory-cache)))
  ([delegate-cache]
   (map->SingleThreadLookupCache
     {:delegate-cache delegate-cache})))

(comment

  (def counter (atom 0))

  ;; An expensive lookup function. It returns a unique value each time it calls.
  (defn lookup-a-value
    []
    (Thread/sleep 1000)
    (swap! counter inc))

  ;; Create a regular memory cache
  (def mem-cache (mc/create-in-memory-cache))

  ;; If we execute a bunch of concurrent requests for a value the lookup will be invoked N times with
  ;; a regular memory cache. The values returned will all be the same due to how the cache works.
  (map deref (for [n (range 10)]
               (future
                 (c/get-value mem-cache :foo lookup-a-value))))

  ;; But the count show it's been called more than 1 time
  (deref counter) ; => 10

  ;; Set the counter back to 0
  (reset! counter 0)

  ;; Create a single threaded lookup cache and start it
  (def slc (l/start (create-single-thread-lookup-cache) nil))

  ;; If we execute a bunch of concurrent requests for a value the lookup will be invoked once.
  (map deref (for [_ (range 10)]
               (future (c/get-value slc :foo lookup-a-value))))

  ;; The counter shows it's been called only once.
  (deref counter) ; => 1

  ;; Stop the cache
  (l/stop slc nil)

  )
