(ns cmr.metadata-db.data.memory-db
  "An in memory implementation of the metadata database."
  (:require [cmr.metadata-db.data.concepts :as concepts]
            [cmr.metadata-db.data.providers :as providers]
            [cmr.common.concepts :as cc]
            [cmr.common.lifecycle :as lifecycle]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [clj-time.format :as f]
            [cmr.common.date-time-parser :as p]))



(defn after-save
  "Handler for save calls. It will be passed the list of concepts and the concept that was just
  saved. It should manipulate anything required and return the new list of concepts."
  [concepts concept]
  (if (and (= :collection (:concept-type concept))
           (:deleted concept))
    (filter #(not= (:concept-id concept) (get-in % [:extra-fields :parent-collection-id]))
            concepts)
    concepts))

(defn- concept->tuple
  "Converts a concept into a concept id revision id tuple"
  [concept]
  [(:concept-id concept) (:revision-id concept)])

(defn validate-concept-id-native-id-not-changing
  "Validates that the concept-id native-id pair for a concept being saved is not changing. This
  should be done within a save transaction to avoid race conditions where we might miss it.
  Returns nil if valid and an error response if invalid."
  [db concept]
  (let [{:keys [concept-id native-id concept-type provider-id]} concept
        {existing-concept-id :concept-id
         existing-native-id :native-id} (->> (deref (:concepts-atom db))
                                             (filter #(= concept-type (:concept-type %)))
                                             (filter #(= provider-id (:provider-id %)))
                                             (filter #(or (= concept-id (:concept-id %))
                                                          (= native-id (:native-id %))))
                                             first)]
    (when (and (and existing-concept-id existing-native-id)
               (or (not= existing-concept-id concept-id)
                   (not= existing-native-id native-id)))
      {:error :concept-id-concept-conflict
       :error-message (format (str "Concept id [%s] and native id [%s] to save do not match "
                                   "existing concepts with concept id [%s] and native id [%s].")
                              concept-id native-id existing-concept-id existing-native-id)
       :existing-concept-id existing-concept-id
       :existing-native-id existing-native-id})))

(defn- provider-not-found-error
  [provider-id]
  {:error :not-found :error-message (format "Provider [%s] does not exist." provider-id)})

(defrecord MemoryDB
  [
   ;; A sequence of concepts stored in metadata db
   concepts-atom

   ;; The next id to use for generating a concept id.
   next-id-atom

   ;; A map of provider ids to providers that exist
   providers-atom
   ]

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

  (get-latest-concepts
    [db concept-type provider-id concept-ids]
    (let [concept-id-set (set concept-ids)
          concept-map (reduce (fn [concept-map {:keys [concept-id revision-id] :as concept}]
                                (if (contains? concept-id-set concept-id)
                                  (cond

                                    (nil? (get concept-map concept-id))
                                    (assoc concept-map concept-id concept)

                                    (> revision-id (:revision-id (get concept-map concept-id)))
                                    (assoc concept-map concept-id concept)

                                    :else
                                    concept-map)
                                  concept-map))
                              {}
                              @concepts-atom)]
      (keep (partial get concept-map) concept-ids)))

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

    (if-let [error (validate-concept-id-native-id-not-changing this concept)]
      ;; There was a concept id, native id mismatch with earlier concepts
      error
      ;; Concept id native id pair was valid
      (let [{:keys [concept-type provider-id concept-id revision-id]} concept
            concept (update-in concept [:revision-date] #(or % (f/unparse (f/formatters :date-time)
                                                                          (tk/now))))]
        (if (or (nil? revision-id)
                (concepts/get-concept this concept-type provider-id concept-id revision-id))
          {:error :revision-id-conflict}
          (do
            (swap! concepts-atom (fn [concepts]
                                   (after-save (conj concepts concept)
                                               concept)))
            nil)))))

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

  (force-delete-concepts
    [db provider-id concept-type concept-id-revision-id-tuples]
    (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
      (concepts/force-delete db concept-type provider-id concept-id revision-id)))

  (get-concept-type-counts-by-collection
    [db concept-type provider-id]
    (->> @concepts-atom
         (filter #(= provider-id (:provider-id %)))
         (filter #(= concept-type (:concept-type %)))
         (group-by (comp :parent-collection-id :extra-fields))
         (map #(update-in % [1] count))
         (into {})))

  (reset
    [db]
    (reset! concepts-atom [])
    (reset! next-id-atom (dec cmr.metadata-db.data.oracle.concepts/INITIAL_CONCEPT_NUM)))

  (get-expired-concepts
    [db provider concept-type]
    (filter
      (fn [c]
        (let [delete-time (get-in c [:extra-fields :delete-time])
              delete-time (when delete-time (p/parse-datetime  delete-time))]
          (and (= provider (:provider-id c))
               (= concept-type (:concept-type c))
               (some? delete-time)
               (t/before? delete-time (tk/now)))))
      @concepts-atom))

  (get-tombstoned-concept-revisions
    [db provider concept-type tombstone-cut-off-date limit]
    (->> @concepts-atom
         (filter #(= concept-type (:concept-type %)))
         (filter #(= provider (:provider-id %)))
         (filter :deleted)
         (filter #(t/before? (p/parse-datetime (:revision-date %)) tombstone-cut-off-date))
         (map #(vector (:concept-id %) (:revision-id %)))
         (take limit)))

  (get-old-concept-revisions
    [db provider concept-type max-versions limit]
    (letfn [(drop-highest
              [concepts]
              (->> concepts
                   (sort-by :revision-id)
                   (drop-last max-versions)))]
      (->> @concepts-atom
           (filter #(= concept-type (:concept-type %)))
           (filter #(= provider (:provider-id %)))
           (group-by :concept-id)
           vals
           (filter #(> (count %) max-versions))
           (mapcat drop-highest)
           (map concept->tuple))))

  providers/ProvidersStore

  (save-provider
    [db {:keys [provider-id] :as provider}]
    (if (@providers-atom provider-id)
      {:error :provider-id-conflict :error-message (format "Provider [%s] already exists." provider-id)}
      (swap! providers-atom assoc provider-id provider)))

  (get-providers
    [db]
    (vals @providers-atom))

  (update-provider
    [db {:keys [provider-id] :as provider}]
    (if (get @providers-atom provider-id)
      (swap! providers-atom assoc provider-id provider)
      (provider-not-found-error provider-id)))

  (delete-provider
    [db provider-id]
    (if (@providers-atom provider-id)
      (swap! providers-atom dissoc provider-id)
      (provider-not-found-error provider-id)))

  (reset-providers
    [db]
    (reset! providers-atom {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-db
  "Creates and returns an in-memory database."
  ([]
   (create-db []))
  ([concepts]
   ;; sort by revision-id reversed so latest will be first
   (->MemoryDB (atom (reverse (sort-by :revision-id concepts)))
               (atom (dec cmr.metadata-db.data.oracle.concepts/INITIAL_CONCEPT_NUM))
               (atom {}))))
