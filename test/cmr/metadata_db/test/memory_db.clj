(ns cmr.metadata-db.test.memory-db
  "Contains a record definition that implements the ConcpetStore protocol
  using fixed return values for testing purposes."
  (:require [cmr.metadata-db.data :as data]))

;;; concept used for tests
(def test-concept {:concept-id "C1000000000-PROV1"
              :concept-type :collection
              :native-id "provider collection id"
              :provider-id "PROV1"
              :metadata "xml here"
              :format "echo10"
              :revision-id 0})

(defrecord MemoryDB
  [db]
  
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data/ConceptStore
  
  (generate-concept-id
    [this concept]
    "C1000000000-PROV1")
  
  
  (get-concept-id
    [this concept-type provider-id native-id]
    "C1000000000-PROV1")
  
  (get-concept-by-provider-id-native-id-concept-type
    [this concept]
    test-concept)
  
  (get-concept
    [this concept-id]
    test-concept)
  
  (get-concept
    [this concept-id revision-id]
    test-concept)
  
  (get-concepts
    [this concept-id-revision-id-tuples]
    [test-concept])
  
  (save
    [this concept]
    (let [revision-id (:revision-id concept)]
      (if (or (nil? revision-id) (= revision-id 1))
        {:concept-id "C1000000000-PROV1" :revision-id 1}
        {:error :revision-id-conflict})))
  
  
  (force-delete
    [this concept-id revision-id]
    )
  
  (reset
    [this]
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-db
  "Creates and returns the database connection pool."
  [db-spec]
  (map->MemoryDB {}))