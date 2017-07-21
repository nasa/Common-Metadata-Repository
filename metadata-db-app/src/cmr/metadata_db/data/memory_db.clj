(ns cmr.metadata-db.data.memory-db
  "An in memory implementation of the metadata database."
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [cmr.common.concepts :as cc]
   [cmr.common.date-time-parser :as p]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.time-keeper :as tk]
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.data.oracle.concepts :as c]
   [cmr.metadata-db.data.providers :as providers]
   [cmr.metadata-db.services.provider-validation :as pv]))

(defmulti after-save
  "Handler for save calls. It will be passed the list of concepts and the
  concept that was just saved. Implementing mehods should manipulate anything
  required and return the new list of concepts."
  (fn [db concepts concept]
    (:concept-type concept)))

(defmethod after-save :collection
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (filter #(not= (:concept-id concept)
                   (get-in % [:extra-fields :parent-collection-id]))
            concepts)))

;; CMR-2520 Readdress this case when asynchronous cascaded deletes are implemented.
(defn delete-associations-after-save
  "A general in-memory db function usable by concepts which need to delete
  associations.

  Note that the logic and workflow is a little different for external
  databases."
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (let [tombstones (->> concept
                          (c/get-tombstone-associations db)
                          (c/update-tombstone-associations))]
      (c/publish-delete-associations db tombstones)
      (concat concepts tombstones))))

(defmethod after-save :tag
  [db concepts concept]
  (delete-associations-after-save db concepts concept))

(defmethod after-save :variable
  [db concepts concept]
  (delete-associations-after-save db concepts concept))

(defmethod after-save :default
  [db concepts concept]
  concepts)

(defn- concept->tuple
  "Converts a concept into a concept id revision id tuple"
  [concept]
  [(:concept-id concept) (:revision-id concept)])

(defn validate-concept-id-native-id-not-changing
  "Validates that the concept-id native-id pair for a concept being saved is not changing. This
  should be done within a save transaction to avoid race conditions where we might miss it.
  Returns nil if valid and an error response if invalid."
  [db provider concept]
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

(defn- delete-time
  "Returns the parsed delete-time extra field of a concept."
  [concept]
  (some-> concept
          (get-in [:extra-fields :delete-time])
          p/parse-datetime))

(defn- expired?
  "Returns true if the concept is expired (delete-time in the past)"
  [concept]
  (when-let [t (delete-time concept)]
    (t/before? t (tk/now))))

(defn- latest-revisions
  "Returns only the latest revisions of each concept in a seq of concepts."
  [concepts]
  (map last
       (map (partial sort-by :revision-id)
            (vals (group-by :concept-id concepts)))))

(defn- concepts->find-result
  "Returns the given concepts in the proper find result format based on the find params."
  [concepts params]
  (let [exclude-metadata? (= "true" (:exclude-metadata params))
        map-fn (fn [concept]
                 (let [concept (if (and (= :granule (:concept-type concept))
                                        (nil? (get-in concept [:extra-fields :granule-ur])))
                                 (assoc-in concept [:extra-fields :granule-ur]
                                           (:native-id concept))
                                 concept)]
                   (if exclude-metadata?
                     (dissoc concept :metadata)
                     concept)))]
    (map map-fn concepts)))

(defrecord MemoryDB
  [
   ;; A sequence of concepts stored in metadata db
   concepts-atom

   ;; The next id to use for generating a concept id.
   next-id-atom

   ;; The next global transaction id
   next-transaction-id-atom

   ;; A map of provider ids to providers that exist
   providers-atom]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start [this system]
         this)

  (stop [this system]
        this)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  concepts/ConceptSearch

  (find-concepts
   [db providers params]
   (let [found-concepts (mapcat #(concepts/search-with-params
                                  @concepts-atom
                                  (assoc params :provider-id (:provider-id %)))
                                providers)]
     (concepts->find-result found-concepts params)))

  (find-latest-concepts
   [db provider params]
   (let [latest-concepts (latest-revisions @concepts-atom)
         found-concepts (concepts/search-with-params
                         latest-concepts
                         (assoc params :provider-id (:provider-id provider)))]
     (concepts->find-result found-concepts params)))

  concepts/ConceptsStore

  (generate-concept-id
   [this concept]
   (let [{:keys [concept-type provider-id]} concept
         num (swap! next-id-atom inc)]
     (cc/build-concept-id {:concept-type concept-type
                           :sequence-number num
                           :provider-id provider-id})))

  (get-concept-id
   [this concept-type provider native-id]
   (let [provider-id (:provider-id provider)
         concept-type (if (keyword? concept-type) concept-type (keyword concept-type))]
     (->> @concepts-atom
          (filter (fn [c]
                    (and (= concept-type (:concept-type c))
                         (= provider-id (:provider-id c))
                         (= native-id (:native-id c)))))
          first
          :concept-id)))

  (get-granule-concept-ids
   [this provider native-id]
   (let [provider-id (:provider-id provider)
         concept-ids-fn (fn [gran]
                          [(:concept-id gran) (get-in gran [:extra-fields :parent-collection-id])])]
     (->> @concepts-atom
          (filter (fn [c]
                    (and (= :granule (:concept-type c))
                         (= provider-id (:provider-id c))
                         (= native-id (:native-id c)))))
          first
          concept-ids-fn)))

  (get-concept
   [db concept-type provider concept-id]
   (let [revisions (filter
                    (fn [c]
                      (and (= concept-type (:concept-type c))
                           (= (:provider-id provider) (:provider-id c))
                           (= concept-id (:concept-id c))))
                    @concepts-atom)]
     (->> revisions
          (sort-by :revision-id)
          last)))

  (get-concept
   [db concept-type provider concept-id revision-id]
   (if-not revision-id
     (concepts/get-concept db concept-type provider concept-id)
     (first (filter
             (fn [c]
               (and (= concept-type (:concept-type c))
                    (= (:provider-id provider) (:provider-id c))
                    (= concept-id (:concept-id c))
                    (= revision-id (:revision-id c))))
             @concepts-atom))))

  (get-concepts
   [this concept-type provider concept-id-revision-id-tuples]
   (filter identity
           (map (fn [[concept-id revision-id]]
                  (concepts/get-concept this concept-type provider concept-id revision-id))
                concept-id-revision-id-tuples)))

  (get-latest-concepts
   [db concept-type provider concept-ids]
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

  (get-transactions-for-concept
   [db provider con-id]
   (keep (fn [{:keys [concept-id revision-id transaction-id]}]
           (when (= con-id concept-id)
             {:revision-id revision-id :transaction-id transaction-id}))
         @concepts-atom))

  (save-concept
   [this provider concept]
   {:pre [(:revision-id concept)]}

   (if-let [error (validate-concept-id-native-id-not-changing this provider concept)]
     ;; There was a concept id, native id mismatch with earlier concepts
     error
     ;; Concept id native id pair was valid
     (let [{:keys [concept-type provider-id concept-id revision-id]} concept
           concept (update-in concept
                              [:revision-date]
                              #(or % (f/unparse (f/formatters :date-time) (tk/now))))
           concept (assoc concept :transaction-id (swap! next-transaction-id-atom inc))
           concept (if (= concept-type :granule)
                     (-> concept
                         (dissoc :user-id)
                         ;; This is not stored in the real db.
                         (update-in [:extra-fields] dissoc :parent-entry-title))
                     concept)]
       (if (or (nil? revision-id)
               (concepts/get-concept this concept-type provider concept-id revision-id))
         {:error :revision-id-conflict}
         (do
           (swap! concepts-atom (fn [concepts]
                                  (after-save this (conj concepts concept)
                                              concept)))
           nil)))))

  (force-delete
   [db concept-type provider concept-id revision-id]
   (swap! concepts-atom
          #(filter
            (fn [c]
              (not (and (= concept-type (:concept-type c))
                        (= (:provider-id provider) (:provider-id c))
                        (= concept-id (:concept-id c))
                        (= revision-id (:revision-id c)))))
            %)))

  (force-delete-concepts
   [db provider concept-type concept-id-revision-id-tuples]
   (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
     (concepts/force-delete db concept-type provider concept-id revision-id)))

  (get-concept-type-counts-by-collection
   [db concept-type provider]
   (->> @concepts-atom
        (filter #(= (:provider-id provider) (:provider-id %)))
        (filter #(= concept-type (:concept-type %)))
        (group-by (comp :parent-collection-id :extra-fields))
        (map #(update-in % [1] count))
        (into {})))

  (reset
   [db]
   (reset! concepts-atom [])
   (reset! next-id-atom (dec cmr.metadata-db.data.oracle.concepts/INITIAL_CONCEPT_NUM))
   (reset! next-transaction-id-atom 1))

  (get-expired-concepts
   [db provider concept-type]
   (->> @concepts-atom
        (filter #(= (:provider-id provider) (:provider-id %)))
        (filter #(= concept-type (:concept-type %)))
        latest-revisions
        (filter expired?)
        (remove :deleted)))

  (get-tombstoned-concept-revisions
   [db provider concept-type tombstone-cut-off-date limit]
   (->> @concepts-atom
        (filter #(= concept-type (:concept-type %)))
        (filter #(= (:provider-id provider) (:provider-id %)))
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
          (filter #(= (:provider-id provider) (:provider-id %)))
          (group-by :concept-id)
          vals
          (filter #(> (count %) max-versions))
          (mapcat drop-highest)
          (map concept->tuple))))

  providers/ProvidersStore

  (save-provider
   [db {:keys [provider-id] :as provider}]
   (swap! providers-atom assoc provider-id provider))

  (get-providers
   [db]
   (vals @providers-atom))

  (get-provider
   [db provider-id]
   (@providers-atom provider-id))

  (update-provider
   [db {:keys [provider-id] :as provider}]
   (swap! providers-atom assoc provider-id provider))

  (delete-provider
   [db provider]
   ;; Cascade to delete the concepts
   (doseq [{:keys [concept-type concept-id revision-id]} (concepts/find-concepts db [provider] nil)]
     (concepts/force-delete db concept-type provider concept-id revision-id))
   ;; to find items that reference the provider that should be deleted (e.g. ACLs)
   (doseq [{:keys [concept-type concept-id revision-id]} (concepts/find-concepts
                                                          db
                                                          [pv/cmr-provider]
                                                          {:target-provider-id (:provider-id provider)})]
     (concepts/force-delete db concept-type pv/cmr-provider concept-id revision-id))
   (swap! providers-atom dissoc (:provider-id provider)))

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
               (atom 0)
               (atom {}))))
