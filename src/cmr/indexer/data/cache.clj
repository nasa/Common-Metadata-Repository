(ns cmr.indexer.data.cache
  "A system level cache based on clojure.core.cache library.
  Follows basic usage pattern as given in - https://github.com/clojure/core.cache/wiki/Using"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.system-trace.context :as context]
            [clojure.core.cache :as cc]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.indexer.api.routes :as routes]))


(defrecord SysCache
  [
   ;; Soft cache for in place updates
   kv-store
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (let [kv-store (:kv-store this)
          index-set-id (get-in idx-set/index-set [:index-set :id])
          concept-indices (idx-set/get-concept-type-index-names index-set-id)
          concept-mapping-types (idx-set/get-concept-mapping-types index-set-id)
          elastic-config (idx-set/get-elastic-config)]
      (when-not (cc/has? kv-store :index-set)
        (cc/miss kv-store :index-set {:concept-indices concept-indices
                                      :concept-mapping-types concept-mapping-types}))
      (when-not (cc/has? kv-store :elastic-config)
        (cc/miss kv-store :elastic-config elastic-config)))
    this)

  (stop
    [this system]
    (doall (for [kv-store (list (:kv-store this))
                 k (keys kv-store)]
             (if (cc/has? kv-store k)
               (cc/evict kv-store k))))
    this))

(defn create-cache
  "Create system level cache. For now accept soft cache for in place updates."
  [cache]
  (map->SysCache {:kv-store cache}))


