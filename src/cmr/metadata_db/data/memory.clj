(ns cmr.metadata-db.data.memory
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]))

;;; Uitility methods
(defn concept-id-for-concept
  "Create a unique id for a concept form its values"
  [{:keys [concept-type provider-id id]}]
  (str concept-type ":" provider-id ":" id))

(defn concept-type-from-concept-id
  "Get a concept type for a given id"
  [concept-id]
  (first (string/split concept-id #":")))

(comment
  (concept-type-from-concept-id "collections:PROV1:some-id")
  )


;;; An in-memory implementation of the provider store
(defrecord InMemoryStore
  [
   ;; An atom containing a map of echo-collection-ids to collections
   concepts]
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle
  
  (start [this system]
         (swap! (:concepts this) assoc :collections {}))
  
  (stop [this system]
        this)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data/ConceptStore
  
  (get-concept
    [this concept-id]
    (let [concepts (:concepts this)
          concept-type (concept-type-from-concept-id concept-id)
          concept-map (get concepts concept-type)
          concept-revisions (get concept-map concept-id)]
      (if-not concept-revisions [] concept-revisions)))
  
  (insert-concept
    [this concept]
    (let [concepts (:concepts this)
          concept-type (:concept-type concept)
          concept-map (get concepts concept-type)
          concept-id (concept-id-for-concept concept)
          concept-revisions (data/get-concept this concept-id)
          new-concept-revisions (conj concept-revisions 
                                      (assoc concept :concept-id concept-id))
          revision (- (count new-concept-revisions) 1)
          new-concept-map (assoc concept-map concept-id new-concept-revisions)]
      (swap! (:concepts this) assoc new-concept-map concept-type)
      (println "THIS:")
      (println this)
      revision))
  
  (delete-concept
    [this concept-type provider-id id]))   

(comment
       (def concepts 
         {:collections {:some-id1 ["A1" "A2" "A3"]
                        :some-id2 ["B1" "B2"]
                        :some-other-id1 ["C1"]}})
       
       (let [concept-map (:collections concepts)
             concept (get concept-map :some-id1)]
         (println concept))
    
  
       )     


(defn create-db
  "Creates the in memory store."
  []
  (map->InMemoryStore {:concepts (atom {})}))                      
  