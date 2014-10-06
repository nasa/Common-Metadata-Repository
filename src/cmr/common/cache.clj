(ns cmr.common.cache
  "A system level cache based on clojure.core.cache library.
  Follows basic usage pattern as given in - https://github.com/clojure/core.cache/wiki/Using"
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [clojure.core.cache :as cc]))

(def general-cache-key
  "The key used to store he general cache in the system cache map."
  :general)

(defn cache-lookup
  "Looks up the value of the cached item using the key. If there is a cache miss it will invoke
  the function given with no arguments, save the value in the cache and return the value."
  [cmr-cache key f]
  (-> (swap! (:atom cmr-cache)
             (fn [cache]
               (if (cc/has? cache key)
                 (cc/hit cache key)
                 (cc/miss cache key (f)))))
      (get key)))

(defn create-cache
  "Create system level cache."
  ([]
   (create-cache (cc/basic-cache-factory {})))
  ([initial-cache]
   {:initial initial-cache
    :atom (atom initial-cache)}))

(defn reset-cache
  [cmr-cache]
  (reset! (:atom cmr-cache) (:initial cmr-cache)))

(defn set-cache!
  [cmr-cache value]
  (swap! (:atom cmr-cache)
         (fn [_] value)))




