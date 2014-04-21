(ns cmr.indexer.data.cache
  "A system level cache based on clojure.core.cache library.
  Follows basic usage pattern as given in - https://github.com/clojure/core.cache/wiki/Using"
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.system-trace.context :as context]
            [clojure.core.cache :as cc]
            [cmr.indexer.data.index-set :as idx-set]))


(defn cache-lookup
  [cache-atom key f]
  (-> (swap! cache-atom
             (fn [cache]
               (if (cc/has? cache key)
                 (cc/hit cache key)
                 (cc/miss cache key (f)))))
      (get key)))

(defn create-cache
  "Create system level cache."
  []
  (atom (cc/lru-cache-factory {})))


