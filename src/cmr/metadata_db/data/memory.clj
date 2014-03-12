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

(defn- save 
  "Save a concept"
  [concept-atom concept concept-type concept-map concept-id revisions]
  (let [new-revisions (conj (or revisions []) concept)
        new-concept-map (assoc concept-map concept-id new-revisions)]
        (swap! concept-atom assoc concept-type new-concept-map)
        (println "ATOM: " concept-atom)
        (- (count new-revisions) 1)))
        
    

(comment
  (concept-type-from-concept-id "collections:PROV1:some-id")
  (or "A" true)
  (conj nil "A")
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
  
  (get-concept-id
    [this concept-type provider-id native-id]
    (str concept-type ":" provider-id ":" native-id))
  
  (get-concept
    [this concept-id, revision-id]
    (let [concepts (:concepts this)
          concept-type (concept-type-from-concept-id concept-id)
          concept-map (get concepts concept-type)
          concept-revisions (get concept-map concept-id)]
      (if-not concept-revisions [] concept-revisions)))
  
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
                                      (throw (Exception. "Invalid revision-id"))
                                      (save concepts concept concept-type concept-map concept-id revisions))
        (and revision-id) (if-not (= revision-id 0)
                            (throw (Exception. "Invalid revision-id"))
                            (save concepts concept concept-type concept-map concept-id revisions))
        :else (save concepts concept concept-type concept-map concept-id revisions)))))
  

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
  