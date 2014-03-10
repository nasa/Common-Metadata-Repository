(ns cmr.metadata-db.data.memory
  (:require #_[cmr.metadata-db.data :as data]
            [cmr.common.lifecycle :as lifecycle]))

;;; An in-memory implementation of the provider store
(defrecord InMemoryStore
  [
   ;; An atom containing a amp of echo-collection-ids to collections
   collection-map]
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start [this system]
         this)

  (stop [this system]
        this))
  
 	
 (defn create-db
  "Creates the in memory store."
  []
  (map->InMemoryStore {:provider-map (atom {})
                       :collection-map (atom {})
                       :granule-map (atom {})}))