(ns cmr.metadata-db.data.memory
  "Contains a record definition that implements the ConcpetStore and Lifecycle protocols to support
  development using an in-memory data store."
  (:require [cmr.metadata-db.data :as data]
            [cmr.metadata-db.data.utility :as util]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]))


;;; Utility methods

(defn- retrieve-concept
  "Get a concept by concept-id. Returns nil if concept is not found."
  [db concept-id, revision-id]
  (let [concepts (:concepts db)
        concept-map (get @concepts (util/concept-type-prefix concept-id))
        revisions (get concept-map concept-id)]
    (when (and revision-id (or (< revision-id 0) (> revision-id (dec (count revisions)))))
      (errors/throw-service-error :not-found "Revision %s" revision-id " does not exist"))
    (if-let [concept (get revisions revision-id)]
      concept
      (last revisions))))

(defn- save 
  "Save a concept"
  [concept-atom concept concept-type concept-map concept-id revisions]
  (let [revision-id (count revisions)
        revised-concept (assoc concept :revision-id revision-id)
        new-revisions (conj (or revisions []) revised-concept)
        new-concept-map (assoc concept-map concept-id new-revisions)]
    (swap! concept-atom assoc (util/concept-type-prefix concept-type) new-concept-map)
    (dec (count new-revisions))))

(defn- create-tombstone-for-concept
  "Create a tombstone from a versioned concept."
  [concept]
  {:concept-type (:concept-type concept)
   :native-id (:native-id concept)
   :concept-id (:concept-id concept)
   :provider-id (:provider-id concept)
   :deleted true
   :revision-id (inc (:revision-id concept))})

(defn- is-tombstone?
  "Check to see if an entry is a tombstone (has a :deleted true entry)."
  [concept]
  (:deleted concept))

(defn- delete
  "Delete a concept (create tombstone)."
  [concept-atom concept-id concept-map revisions]
  (let [{:keys [concept-prefix]} (cutil/parse-concept-id concept-id)
        concept (last revisions)
        tombstone (create-tombstone-for-concept concept)
        new-revisions (conj revisions tombstone)
        new-concept-map (assoc concept-map concept-id new-revisions)]
    (if (is-tombstone? concept)
      (:revision-id concept)
      (do (swap! concept-atom assoc concept-prefix new-concept-map)
        (:revision-id tombstone)))))


(defn- validate-concept
  "Validate that a concept has the fields we need to save it."
  [concept]
  (if-not (:concept-type concept)
    (errors/throw-service-error :invalid-data "Concept must include concept-type"))
  (if-not (:concept-id concept)
    (errors/throw-service-error :invalid-data "Concept must include concept-id")))

(defn- reset-database
  "Empty the database."
  [db]
  (swap! (:concepts db) empty)
  (reset! (:concept-id-seq db) 0))

(defn- concept-id-seq 
  "Returns a monotonically increasing number."
  [db]
  (swap! (:concept-id-seq db) inc))

;;; An in-memory implementation of the provider store
(defrecord InMemoryStore
  [
   ;; An atom containing maps of concept-ids to concepts
   concepts]
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle
  
  (start [this system]
         (reset-database this)
         this)
  
  (stop [this system]
        this)
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data/ConceptStore
  
  (get-concept-id
    [this concept-type provider-id native-id]
    ;; We don't use the native-id for the in-memory implementation.
    ;; Other implementatations may want to use it.
    (let [type-prefix (util/concept-type-prefix concept-type)
          stored-ids (:concept-concept-id this)
          concept-concept-id-key (str type-prefix provider-id native-id)
          stored-id (get @stored-ids concept-concept-id-key)]
      (if stored-id
        stored-id
        (let [seq-num (concept-id-seq this)
              generated-id (str type-prefix seq-num "-" provider-id)]
          (swap! stored-ids assoc concept-concept-id-key generated-id)
          generated-id))))
  
  (get-concept
    [this concept-id revision-id]
    (if-let [concept (retrieve-concept this concept-id revision-id)]
      concept
      (if revision-id
        (errors/throw-service-error :not-found
                                    "Could not find concept with concept-id of %s and revision %s."
                                    concept-id
                                    revision-id)
        (errors/throw-service-error :not-found
                                    "Could not find concept with concept-id of %s."
                                    concept-id))))
  
  (get-concepts
    [this concept-id-revision-id-tuples]
    ;; An SQL based DB would have a more efficient way to do this, but
    ;; an in-memory map like this has to pull things back one-by-one.
    (remove nil? (map #(try (retrieve-concept this (first %) (last %))
                         (catch Exception e nil)) concept-id-revision-id-tuples)))
  
  (save-concept
    [this concept]
    (validate-concept concept)
    (let [{:keys [concept-type concept-id revision-id]} concept
          concepts (:concepts this)
          concept-map (get @concepts (util/concept-type-prefix concept-type))
          revisions (get concept-map concept-id [])]
      (when (and revision-id
                 (not= (count revisions) revision-id))
        (errors/throw-service-error :conflict 
                                    "Expected revision-id of %s got %s"
                                    (count revisions)
                                    revision-id))
      (save concepts concept concept-type concept-map concept-id revisions)))
  
  (delete-concept
    [this concept-id]
    (let [concepts (:concepts this)
          concept-prefix (:concept-prefix (cutil/parse-concept-id concept-id))
          concept-map (get @concepts concept-prefix)
          revisions (get concept-map concept-id)]
      (when (nil? revisions)
        (errors/throw-service-error :not-found
                                    "Concept %s does not exist."
                                    concept-id))
      (delete concepts concept-id concept-map revisions)))
  
  (force-delete
    [this]
    (reset-database this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;     

(defn create-db
  "Creates the in memory store."
  []
  (map->InMemoryStore {:concept-id-seq (atom 0) ;; number seqeunce generator for id generation
                       :concept-concept-id (atom {}) ;; generated ids
                       :concepts (atom {})})) ;; actual stored concepts                   
