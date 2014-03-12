(ns cmr.metadata-db.data.memory
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]))

;;; Uitility methods

(defn- save 
  "Save a concept"
  [concept-atom concept concept-type concept-map concept-id revisions]
  (let [new-revisions (conj (or revisions []) concept)
        new-concept-map (assoc concept-map concept-id new-revisions)]
    (swap! concept-atom assoc concept-type new-concept-map)
    (- (count new-revisions) 1)))
        
    

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
  
  (get-concept-id
    [this concept-type provider-id native-id])
  
  (get-concept
    [this concept-id, revision-id])
  
  (get-concepts
    [this concept-id-revision-id-tuples])
  
  (save-concept
    [this concept]
    (let [{:strs [concept-type concept-id revision-id]} concept
          concepts (:concepts this)
          concept-map (get @concepts concept-type)
          revisions (get concept-map concept-id)]
      (cond
        (and revisions revision-id) (if-not (= (count revisions) 
                                               revision-id)
                                      (throw (Exception. (str "Invalid revision-id. Expected: " (count revisions))))
                                      (save concepts concept concept-type concept-map concept-id revisions))
        (and revision-id) (if-not (= revision-id 0)
                            (throw (Exception. "Invalid revision-id. Expected: 0"))
                            (save concepts concept concept-type concept-map concept-id revisions))
        :else (save concepts concept concept-type concept-map concept-id revisions)))))


(defn create-db
  "Creates the in memory store."
  []
  (map->InMemoryStore {:concepts (atom {})}))                      
  