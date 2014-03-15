(ns cmr.metadata-db.data.memory
  "Contains a record definition that implements the ConcpetStore and Lifecycle protocols to support
  development using an in-memory data store."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [cmr.common.services.errors :as errors]))

;;; Uitility methods

(defn- save 
  "Save a concept"
  [concept-atom concept concept-type concept-map concept-id revisions]
  (let [new-revisions (conj (or revisions []) concept)
        new-concept-map (assoc concept-map concept-id new-revisions)]
    (swap! concept-atom assoc concept-type new-concept-map)
    (dec (count new-revisions))))

(defn- validate-concept
  "Validate that a concept has the fields we need to save it."
  [concept]
  (if-not (:concept-type concept)
    (errors/throw-service-error :invalid-data "Concept must include concept-type"))
  (if-not (:concept-id concept)
    (errors/throw-service-error :invalid-data "Concept must include concept-id")))
        
    

;;; An in-memory implementation of the provider store
(defrecord InMemoryStore
  [
   ;; An atom containing a map of echo-collection-ids to collections
   concepts]
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle
  
  (start [this system]
         (swap! (:concepts this) assoc :collections {})
         this)
  
  (stop [this system]
        this)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data/ConceptStore
  
  (get-concept-id
    [this concept-type provider-id native-id])
  
  (get-concept
    [this concept-id, revision-id])
  
  (get-concepts
    [this concept-id-revision-id-tuples])
  
  (save-concept
    [this concept]
    (validate-concept concept)
    (let [{:keys [concept-type concept-id revision-id]} concept
          concepts (:concepts this)
          concept-map (get @concepts concept-type)
          revisions (get concept-map concept-id [])]
      (when (and revision-id
                 (not= (count revisions) revision-id))
        (errors/throw-service-error :conflict 
                                    "Expected revision-id of %s got %s"
                                    (count revisions)
                                    revision-id))
      (save concepts concept concept-type concept-map concept-id revisions))))
        


(defn create-db
  "Creates the in memory store."
  []
  (map->InMemoryStore {:concepts (atom {})}))                      
  