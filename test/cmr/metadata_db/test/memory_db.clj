(ns cmr.metadata-db.test.memory-db
  "Contains a record definition that implements the ConcpetStore protocol
  using fixed return values for testing purposes."
  (:require [cmr.metadata-db.data.concepts :as concepts]
            [cmr.common.concepts :as cc]))

;;; concept used for tests
(def test-concept {:concept-id "C1000000000-PROV1"
                   :concept-type :collection
                   :native-id "provider collection id"
                   :provider-id "PROV1"
                   :metadata "xml here"
                   :format "echo10"
                   :revision-id 0})

(defrecord MemoryDB
  [concepts-atom
   next-id-atom]

  concepts/ConceptsStore

  (generate-concept-id
    [this concept]
    (let [{:keys [concept-type provider-id]} concept
          num (swap! next-id-atom inc)]
      (cc/build-concept-id {:concept-type concept-type
                            :sequence-number num
                            :provider-id provider-id})))

  (get-concept-id
    [this concept-type provider-id native-id]
    (->> @concepts-atom
         (filter (fn [c]
                   (and (= concept-type (:concept-type c))
                        (= provider-id (:provider-id c))
                        (= native-id (:native-id c)))))
         first
         :concept-id))

  (get-concept
    [db concept-type provider-id concept-id]
    (let [revisions (filter
                      (fn [c]
                        (and (= concept-type (:concept-type c))
                             (= provider-id (:provider-id c))
                             (= concept-id (:concept-id c))))
                      @concepts-atom)]
      (->> revisions
           (sort-by :revision-id)
           last)))

  (get-concept
    [db concept-type provider-id concept-id revision-id]
    (first (filter
             (fn [c]
               (and (= concept-type (:concept-type c))
                    (= provider-id (:provider-id c))
                    (= concept-id (:concept-id c))
                    (= revision-id (:revision-id c))))
             @concepts-atom)))

  (get-concept-by-provider-id-native-id-concept-type
    [this concept]
    (let [{:keys [concept-type provider-id native-id]} concept]
      (->> @concepts-atom
           (filter (fn [c]
                     (and (= concept-type (:concept-type c))
                          (= provider-id (:provider-id c))
                          (= native-id (:native-id c)))))
           first)))

  (get-concepts
    [this concept-type provider-id concept-id-revision-id-tuples]
    (map (fn [[concept-id revision-id]]
           (concepts/get-concept this concept-type provider-id concept-id revision-id))
         concept-id-revision-id-tuples))

  (save-concept
    [this concept]
    {:pre [(:revision-id concept)]}

    (let [{:keys [concept-type provider-id concept-id revision-id]} concept]
      (if (or (nil? revision-id)
              (concepts/get-concept this concept-type provider-id concept-id revision-id))
        {:error :revision-id-conflict}
        (do
          (swap! concepts-atom conj concept)
          nil))))

  (force-delete
    [db concept-type provider-id concept-id revision-id]
    ;;does nothing
    )

  (reset
    [db]
    (reset! concepts-atom [])
    (reset! next-id-atom 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-db
  "Creates and returns an in-memory database."
  ([]
   (create-db [test-concept]))
  ([concepts]
   ;; sort by revision-id reversed so latest will be first
   (->MemoryDB (atom (reverse (sort-by :revision-id concepts))) (atom 0))))