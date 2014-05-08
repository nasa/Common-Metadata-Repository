(ns cmr.metadata-db.data.memory-db
  "An in memory implementation of the metadata database."
  (:require [cmr.metadata-db.data.concepts :as concepts]
            [cmr.metadata-db.data.providers :as providers]
            [cmr.common.concepts :as cc]
            [cmr.common.lifecycle :as lifecycle]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.metadata-db.data.oracle.concepts]))



(defn after-save
  "Handler for save calls. It will be passed the list of concepts and the concept that was just
  saved. It should manipulate anything required and return the new list of concepts."
  [concepts concept]
  (if (and (= :collection (:concept-type concept))
           (:deleted concept))
    (filter #(not= (:concept-id concept) (get-in % [:extra-fields :parent-collection-id]))
            concepts)
    concepts))


(defrecord MemoryDB
  [concepts-atom
   next-id-atom
   providers-atom]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start [this system]
         this)

  (stop [this system]
        this)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
    (let [concept-type (if (keyword? concept-type) concept-type (keyword concept-type))]
      (->> @concepts-atom
           (filter (fn [c]
                     (and (= concept-type (:concept-type c))
                          (= provider-id (:provider-id c))
                          (= native-id (:native-id c)))))
           first
           :concept-id)))

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
    (if-not revision-id
      (concepts/get-concept db concept-type provider-id concept-id)
      (first (filter
               (fn [c]
                 (and (= concept-type (:concept-type c))
                      (= provider-id (:provider-id c))
                      (= concept-id (:concept-id c))
                      (= revision-id (:revision-id c))))
               @concepts-atom))))

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
    (filter identity
            (map (fn [[concept-id revision-id]]
                   (concepts/get-concept this concept-type provider-id concept-id revision-id))
                 concept-id-revision-id-tuples)))

  (find-concepts
    [db params]
    (let [{:keys [concept-type provider-id]} params
          extra-field-params (dissoc params :concept-type :provider-id)]
      (filter (fn [{extra-fields :extra-fields
                    ct :concept-type pid :provider-id}]
                (and (= concept-type ct)
                     (= provider-id pid)
                     (= extra-field-params (select-keys extra-fields (keys extra-field-params)))))
              @concepts-atom)))

  (save-concept
    [this concept]
    {:pre [(:revision-id concept)]}

    (let [{:keys [concept-type provider-id concept-id revision-id]} concept
          concept (assoc concept :revision-date (f/unparse (f/formatters :date-time) (t/now)))]
      (if (or (nil? revision-id)
              (concepts/get-concept this concept-type provider-id concept-id revision-id))
        {:error :revision-id-conflict}
        (do
          (swap! concepts-atom (fn [concepts]
                                 (after-save (conj concepts concept)
                                             concept)))
          nil))))

  (force-delete
    [db concept-type provider-id concept-id revision-id]
    (swap! concepts-atom
           #(filter
              (fn [c]
                (not (and (= concept-type (:concept-type c))
                          (= provider-id (:provider-id c))
                          (= concept-id (:concept-id c))
                          (= revision-id (:revision-id c)))))
              %)))

  (reset
    [db]
    (reset! concepts-atom [])
    (reset! next-id-atom (dec cmr.metadata-db.data.oracle.concepts/INITIAL_CONCEPT_NUM)))

  providers/ProvidersStore

  (save-provider
    [db provider-id]
    (if (@providers-atom provider-id)
      {:error :provider-id-conflict :error-message (format "Provider [%s] already exists." provider-id)}
      (swap! providers-atom conj provider-id)))

  (get-providers
    [db]
    @providers-atom)

  (delete-provider
    [db provider-id]
    (if (@providers-atom provider-id)
      (swap! providers-atom disj provider-id)
      {:error :not-found :error-message (format "Provider [%s] does not exist." provider-id)}))

  (reset-providers
    [db]
    (reset! providers-atom #{})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-db
  "Creates and returns an in-memory database."
  ([]
   (create-db []))
  ([concepts]
   ;; sort by revision-id reversed so latest will be first
   (->MemoryDB (atom (reverse (sort-by :revision-id concepts)))
               (atom (dec cmr.metadata-db.data.oracle.concepts/INITIAL_CONCEPT_NUM))
               (atom #{}))))