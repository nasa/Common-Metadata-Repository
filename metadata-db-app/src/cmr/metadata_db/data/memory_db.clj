(ns cmr.metadata-db.data.memory-db
  "An in memory implementation of the metadata database."
  (:require
   [clj-time.core :as t]
   [clojure.string :as string]
   [cmr.common.concepts :as cc]
   [cmr.common.date-time-parser :as p]
   [cmr.common.generics :as common-generic]
   [cmr.common.memory-db.connection :as connection]
   [cmr.common.time-keeper :as tk]
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.data.oracle.concepts.tag :as tag]
   [cmr.metadata-db.data.oracle.concepts]
   [cmr.metadata-db.data.providers :as providers]
   [cmr.metadata-db.data.util :refer [INITIAL_CONCEPT_NUM]]
   [cmr.metadata-db.services.provider-validation :as pv])
  (:import
   (cmr.common.memory_db.connection MemoryStore)))

;; XXX find-latest-concepts is used by after-save which is defined before
;;     find-latest-concepts is. This bears closer examination, since the
;;     need for a declare due to issues like this is often a signifier
;;     for an API that hasn't been fully ironed out.
(declare find-latest-concepts)

(defn- association->tombstone
  "Returns the tombstoned revision of the given association concept"
  [association]
  (-> association
      (assoc :metadata "" :deleted true)
      (update :revision-id inc)))

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
(defmethod after-save :tag
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    ;; XXX The use of tag/ here is calling into the Oracle implementation; the
    ;;     in-mem db needs its own implementation of that function (they can't
    ;;     share a utility version of it due to the fact that it depends upon
    ;;     a call to another function which is implementation specific).
    (let [tag-associations (tag/get-tag-associations-for-tag-tombstone db concept)
          tombstones (map association->tombstone tag-associations)]
      ;; publish tag-association delete events
      (doseq [tombstone tombstones]
        (ingest-events/publish-event
         (:context db)
         (ingest-events/concept-delete-event tombstone)))
      (concat concepts tombstones))))

;; CMR-2520 Readdress this case when asynchronous cascaded deletes are implemented.
(defmethod after-save :variable
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (let [variable-associations (find-latest-concepts
                                 db
                                 {:provider-id "CMR"}
                                 {:concept-type :variable-association
                                  :variable-concept-id (:concept-id concept)})
          tombstones (map association->tombstone variable-associations)]
      ;; publish variable-association delete events
      (doseq [tombstone tombstones]
        (ingest-events/publish-event
         (:context db)
         (ingest-events/concept-delete-event tombstone)))
      (concat concepts tombstones))))

(defmethod after-save :service
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (let [service-associations (find-latest-concepts
                                db
                                {:provider-id "CMR"}
                                {:concept-type :service-association
                                 :service-concept-id (:concept-id concept)})
          tombstones (map association->tombstone service-associations)]
      ;; publish service-association delete events
      (doseq [tombstone tombstones]
        (ingest-events/publish-event
         (:context db)
         (ingest-events/concept-delete-event tombstone)))
      (concat concepts tombstones))))

(defmethod after-save :tool
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (let [tool-associations (find-latest-concepts
                                db
                                {:provider-id "CMR"}
                                {:concept-type :tool-association
                                 :tool-concept-id (:concept-id concept)})
          tombstones (map association->tombstone tool-associations)]
      ;; publish tool-association delete events
      (doseq [tombstone tombstones]
        (ingest-events/publish-event
         (:context db)
         (ingest-events/concept-delete-event tombstone)))
      (concat concepts tombstones))))

(defmethod after-save :default
  [db concepts concept]
  concepts)

(defn- concept->tuple
  "Converts a concept into a concept id revision id tuple"
  [concept]
  [(:concept-id concept) (:revision-id concept)])

(defmulti find-existing-concept
  "Returns the existing concept in db that matches the given concept-id or native-id."
  (fn [db concept-type provider-id concept-id native-id]
    concept-type))

(defmethod find-existing-concept :subscription
  [db concept-type provider-id concept-id native-id]
  (->> @(:concepts-atom db)
       (filter #(= concept-type (:concept-type %)))
       (filter #(or (= concept-id (:concept-id %))
                    (= native-id (:native-id %))))
       first))

(defmethod find-existing-concept :default
  [db concept-type provider-id concept-id native-id]
  (->> @(:concepts-atom db)
       (filter #(= concept-type (:concept-type %)))
       (filter #(= provider-id (:provider-id %)))
       (filter #(or (= concept-id (:concept-id %))
                    (= native-id (:native-id %))))
       first))

(defn validate-concept-id-native-id-not-changing
  "Validates that the concept-id native-id pair for a concept being saved is not changing. This
  should be done within a save transaction to avoid race conditions where we might miss it.
  Returns nil if valid and an error response if invalid."
  [db provider concept]
  (let [{:keys [concept-id native-id concept-type provider-id]} concept
        {existing-concept-id :concept-id
         existing-native-id :native-id} (find-existing-concept
                                         db concept-type provider-id concept-id native-id)]
    (when (and (and existing-concept-id existing-native-id)
               (or (not= existing-concept-id concept-id)
                   (not= existing-native-id native-id)))
      {:error :concept-id-concept-conflict
       :error-message (format (str "Concept id [%s] and native id [%s] to save do not match "
                                   "existing concepts with concept id [%s] and native id [%s].")
                              concept-id native-id existing-concept-id existing-native-id)
       :existing-concept-id existing-concept-id
       :existing-native-id existing-native-id})))

(defn- concept-id-in-list?
  "Check if the concept-id is in the list of concept-ids."
  [list concept-id]
  (some #(= concept-id %) list))

(defn validate-collection-not-associated-with-another-variable-with-same-name
  "Validates that collection in the concept is not associated with a different
  variable, which has the same name as the variable in the concept.
  Returns nil if valid and an error response if invalid."
  [db concept]
  (let [variable-concept-id (get-in concept [:extra-fields :variable-concept-id])
        associated-concept-id (get-in concept [:extra-fields :associated-concept-id])]
    (when (and variable-concept-id associated-concept-id)
      (let [;;get variable name for the variable-concept-id from cmr_variables.
            variable (->> @(:concepts-atom db)
                          (filter #(= variable-concept-id (:concept-id %)))
                          first)
            variable-name (get-in variable [:extra-fields :variable-name])

            ;;get all the variable associations with variable concept id being different from variable-concept-id
            ;;and associated with the associated-concept-id, from cmr_associations
            v-associations (->> @(:concepts-atom db)
                                (filter #(not= variable-concept-id (get-in % [:extra-fields :variable-concept-id])))
                                (filter #(= associated-concept-id (get-in % [:extra-fields :associated-concept-id]))))
            v-concept-ids-in-associations (map #(get-in % [:extra-fields :variable-concept-id]) v-associations)

            ;;get all the deleted variable associations with variable concept id being different from variable-concept-id
            ;;and associated with the associated-concept-id, from cmr_association
            deleted-v-associations (->> @(:concepts-atom db)
                                        (filter #(not= variable-concept-id (get-in % [:extra-fields :variable-concept-id])))
                                        (filter #(= associated-concept-id (get-in % [:extra-fields :associated-concept-id])))
                                        (filter #(= true (:deleted %))))
            v-concept-ids-in-deleted-associations (map #(get-in % [:extra-fields :variable-concept-id]) deleted-v-associations)

            ;;find the first v-concept-id in v-concept-ids that has variable name being variable-name.
            ;;from cmr_variables table.
            v-concept-id-same-name (->> @(:concepts-atom db)
                                        (filter #(concept-id-in-list?
                                                   v-concept-ids-in-associations
                                                   (:concept-id %)))
                                        (filter #(not (concept-id-in-list?
                                                        v-concept-ids-in-deleted-associations
                                                        (:concept-id %))))
                                        (filter #(= variable-name (get-in % [:extra-fields :variable-name])))
                                        first
                                        :concept-id)]
        (when v-concept-id-same-name
          {:error :collection-associated-with-variable-same-name
           :error-message (format (str "Variable [%s] and collection [%s] can not be associated "
                                       "because the collection is already associated with another variable [%s] with same name.")
                                  variable-concept-id associated-concept-id v-concept-id-same-name)})))))

(defn validate-same-provider-variable-association
  "Validates that variable and the collection in the concept being saved are from the same provider.
  Returns nil if valid and an error response if invalid."
  [concept]
  (let [variable-concept-id (get-in concept [:extra-fields :variable-concept-id])
        associated-concept-id (get-in concept [:extra-fields :associated-concept-id])]
    (when (and variable-concept-id associated-concept-id)
      (let [v-provider-id (second (string/split variable-concept-id #"-"))
            c-provider-id (second (string/split associated-concept-id #"-"))]
        (when (not= v-provider-id c-provider-id)
          {:error :variable-association-not-same-provider
           :error-message (format (str "Variable [%s] and collection [%s] can not be associated "
                                       "because they do not belong to the same provider.")
                                  variable-concept-id associated-concept-id)})))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB ConceptSearch Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-concepts
  [db providers params]
  ;; XXX Looking at search-with-params, seems like it might need to be
  ;;     in a utility ns for use by all impls
  (let [is-subscription? (= :subscription (:concept-type params))
        found-concepts (mapcat #(concepts/search-with-params
                                 @(:concepts-atom db)
                                 (if is-subscription?
                                   params
                                   (assoc params :provider-id (:provider-id %))))
                               providers)]
    (concepts->find-result found-concepts params)))

(defn find-latest-concepts
  [db provider params]
  (let [latest-concepts (latest-revisions @(:concepts-atom db))
        params  (if (= :subscription (:concept-type params))
                  params
                  (assoc params :provider-id (:provider-id provider)))
        found-concepts (concepts/search-with-params
                        latest-concepts
                        params)]
    (concepts->find-result found-concepts params)))

(def concept-search-behaviour
  {:find-concepts find-concepts
   :find-latest-concepts find-latest-concepts})

(extend MemoryStore
        concepts/ConceptSearch
        concept-search-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB ConceptsStore Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn generate-concept-id
  [db concept]
  (let [{:keys [concept-type provider-id]} concept
        num (swap! (:next-id-atom db) inc)]
    (cc/build-concept-id {:concept-type concept-type
                          :sequence-number num
                          :provider-id provider-id})))

(defn get-concept-id
  [db concept-type provider native-id]
  (let [provider-id (:provider-id provider)
        concept-type (if (keyword? concept-type) concept-type (keyword concept-type))]
    (->> @(:concepts-atom db)
         (filter (fn [c]
                   (and
                    (= concept-type (:concept-type c))
                    (= provider-id (:provider-id c))
                    (= native-id (:native-id c)))))
         first
         :concept-id)))

(defn get-granule-concept-ids
  [db provider native-id]
  (let [provider-id (:provider-id provider)
        matched-gran (->> @(:concepts-atom db)
                          (filter (fn [c]
                                   (and (= :granule (:concept-type c))
                                        (= provider-id (:provider-id c))
                                        (= native-id (:native-id c)))))
                          (sort-by :revisoin-id)
                          last)
        {:keys [concept-id deleted]} matched-gran
        parent-collection-id (get-in matched-gran [:extra-fields :parent-collection-id])]
    [concept-id parent-collection-id deleted]))

(defn- -get-concept
  [db concept-type provider concept-id]
  (let [revisions (filter
                   (fn [c]
                    (and (= concept-type (:concept-type c))
                         (= (:provider-id provider) (:provider-id c))
                         (= concept-id (:concept-id c))))
                   @(:concepts-atom db))]
    (->> revisions
         (sort-by :revision-id)
         last)))

(defn- -get-concept-with-revision
  [db concept-type provider concept-id revision-id]
  (if-not revision-id
    (-get-concept db concept-type provider concept-id)
    (first (filter
            (fn [c]
             (and (= concept-type (:concept-type c))
                  (= (:provider-id provider) (:provider-id c))
                  (= concept-id (:concept-id c))
                  (= revision-id (:revision-id c))))
            @(:concepts-atom db)))))

(defn get-concept
  ([db concept-type provider concept-id]
   (-get-concept db concept-type provider concept-id))
  ([db concept-type provider concept-id revision-id]
   (-get-concept-with-revision
    db concept-type provider concept-id revision-id)))

(defn get-concepts
  [db concept-type provider concept-id-revision-id-tuples]
  (filter
   identity
   (map (fn [[concept-id revision-id]]
          (get-concept db concept-type provider concept-id revision-id))
        concept-id-revision-id-tuples)))

(defn get-latest-concepts
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
                           @(:concepts-atom db))]
  (keep (partial get concept-map) concept-ids)))

(defn get-transactions-for-concept
  [db provider con-id]
  (keep (fn [{:keys [concept-id revision-id transaction-id]}]
         (when (= con-id concept-id)
           {:revision-id revision-id :transaction-id transaction-id}))
       @(:concepts-atom db)))

(defn save-concept
  [db provider concept]
  {:pre [(:revision-id concept)]}

  (if-let [error (or (validate-concept-id-native-id-not-changing db provider concept)
                     (when (= :variable-association (:concept-type concept))
                       (or (validate-same-provider-variable-association concept)
                           (validate-collection-not-associated-with-another-variable-with-same-name db concept))))]
   ;; There was a concept id, native id mismatch with earlier concepts
   error
   ;; Concept id native id pair was valid
   (let [{:keys [concept-type provider-id concept-id revision-id]} concept
         concept (update-in concept
                            [:revision-date]
                            #(or % (p/clj-time->date-time-str (tk/now))))
         ;; Set the created-at time to the current timekeeper time for concepts which have
         ;; the created-at field and do not already have a :created-at time set.
         ;; :generic is included for undeclared generic documents which may not specify one
         ;; of the types in latest-approved-document-types.
         concepts-with-created-at
         (into [:collection :granule :service :tool :variable :subscription :generic]
               (common-generic/latest-approved-document-types))
         concept (if (some #{concept-type} concepts-with-created-at)
                   (update-in concept
                              [:created-at]
                              #(or % (p/clj-time->date-time-str (tk/now))))
                   concept)
         concept (assoc concept :transaction-id (swap! (:next-transaction-id-atom db) inc))
         concept (if (= concept-type :granule)
                   (-> concept
                       (dissoc :user-id)
                       ;; This is not stored in the real db.
                       (update-in [:extra-fields] dissoc :parent-entry-title))
                   concept)]
     (if (or (nil? revision-id)
             (get-concept db concept-type provider concept-id revision-id))
       {:error :revision-id-conflict}
       (do
         (swap! (:concepts-atom db) (fn [concepts]
                                     (after-save db (conj concepts concept)
                                                 concept)))
         nil)))))

(defn force-delete
  [db concept-type provider concept-id revision-id]
  (swap! (:concepts-atom db)
         #(filter
           (fn [c]
            (not (and (= concept-type (:concept-type c))
                      (= (:provider-id provider) (:provider-id c))
                      (= concept-id (:concept-id c))
                      (= revision-id (:revision-id c)))))
           %)))

(defn force-delete-concepts
  [db provider concept-type concept-id-revision-id-tuples]
  (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
    (force-delete db concept-type provider concept-id revision-id)))

(defn get-concept-type-counts-by-collection
  [db concept-type provider]
  (->> @(:concepts-atom db)
      (filter #(= (:provider-id provider) (:provider-id %)))
      (filter #(= concept-type (:concept-type %)))
      (group-by (comp :parent-collection-id :extra-fields))
      (map #(update-in % [1] count))
      (into {})))

(defn reset
  [db]
  (reset! (:concepts-atom db) [])
  ;; XXX WAT; no calling into other implementations; split this out into a common ns
  (reset! (:next-id-atom db) (dec INITIAL_CONCEPT_NUM))
  (reset! (:next-transaction-id-atom db) 1))

(defn get-expired-concepts
  [db provider concept-type]
  (->> @(:concepts-atom db)
       (filter #(= (:provider-id provider) (:provider-id %)))
       (filter #(= concept-type (:concept-type %)))
       latest-revisions
       (filter expired?)
       (remove :deleted)))

(defn get-tombstoned-concept-revisions
  [db provider concept-type tombstone-cut-off-date limit]
  (->> @(:concepts-atom db)
       (filter #(= concept-type (:concept-type %)))
       (filter #(= (:provider-id provider) (:provider-id %)))
       (filter :deleted)
       (filter #(t/before? (p/parse-datetime (:revision-date %)) tombstone-cut-off-date))
       (map #(vector (:concept-id %) (:revision-id %)))
       (take limit)))

(defn get-old-concept-revisions
  [db provider concept-type max-versions limit]
  (letfn [(drop-highest
           [concepts]
           (->> concepts
                (sort-by :revision-id)
                (drop-last max-versions)))]
   (->> @(:concepts-atom db)
        (filter #(= concept-type (:concept-type %)))
        (filter #(= (:provider-id provider) (:provider-id %)))
        (group-by :concept-id)
        vals
        (filter #(> (count %) max-versions))
        (mapcat drop-highest)
        (map concept->tuple))))

(def concept-store-behaviour
  {:generate-concept-id generate-concept-id
   :get-concept-id get-concept-id
   :get-granule-concept-ids get-granule-concept-ids
   :get-concept get-concept
   :get-concepts get-concepts
   :get-latest-concepts get-latest-concepts
   :get-transactions-for-concept get-transactions-for-concept
   :save-concept save-concept
   :force-delete force-delete
   :force-delete-concepts force-delete-concepts
   :get-concept-type-counts-by-collection get-concept-type-counts-by-collection
   :reset reset
   :get-expired-concepts get-expired-concepts
   :get-tombstoned-concept-revisions get-tombstoned-concept-revisions
   :get-old-concept-revisions get-old-concept-revisions})

(extend MemoryStore
        concepts/ConceptsStore
        concept-store-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB ProvidersStore Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  [db {:keys [provider-id] :as provider}]
  (swap! (:providers-atom db) assoc provider-id provider))

(defn get-providers
  [db]
  (vals @(:providers-atom db)))

(defn get-provider
  [db provider-id]
  (@(:providers-atom db) provider-id))

(defn update-provider
  [db {:keys [provider-id] :as provider}]
  (swap! (:providers-atom db) assoc provider-id provider))

(defn delete-provider
  [db provider]
  ;; Cascade to delete the concepts
  (doseq [{:keys [concept-type concept-id revision-id]} (find-concepts db [provider] nil)]
   (force-delete db concept-type provider concept-id revision-id))

  ;; Cascade to delete the variable associations, service associations and tool associations
  ;; this is a hacky way of doing things
  (doseq [assoc-type [:variable-association :service-association :tool-association]]
   (doseq [association (find-concepts db [{:provider-id "CMR"}]
                                         {:concept-type assoc-type})]
     (let [{:keys [concept-id revision-id extra-fields]} association
           {:keys [associated-concept-id variable-concept-id service-concept-id tool-concept-id]}
                  extra-fields
           referenced-providers
            (map (fn [cid]
                   (some-> cid
                    cc/parse-concept-id
                    :provider-id))
                 [associated-concept-id variable-concept-id service-concept-id tool-concept-id])]
       ;; If the association references the deleted provider through
       ;; either collection or variable/service/tool, delete the association
       (when (some #{(:provider-id provider)} referenced-providers)
         (force-delete db assoc-type {:provider-id "CMR"} concept-id revision-id)))))

  ;; to find items that reference the provider that should be deleted (e.g. ACLs)
  (doseq [{:keys [concept-type concept-id revision-id]} (find-concepts
                                                         db
                                                         [pv/cmr-provider]
                                                         {:target-provider-id (:provider-id provider)})]
   (force-delete db concept-type pv/cmr-provider concept-id revision-id))
  ;; finally delete the provider
  (swap! (:providers-atom db) dissoc (:provider-id provider)))

(defn reset-providers
  [db]
  (reset! (:providers-atom db) {}))

(def provider-store-behaviour
  {:save-provider save-provider
   :get-providers get-providers
   :get-provider get-provider
   :update-provider update-provider
   :delete-provider delete-provider
   :reset-providers reset-providers})

(extend MemoryStore
        providers/ProvidersStore
        provider-store-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MemoryStore Constructor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db
  "Creates and returns an in-memory database.

  Note that a wrapper is used here in order to support default initial values
  that are only available in cmr.metadata-db.*."
  ([]
   (create-db []))
  ([concepts]
   (connection/create-db
    {:concepts-atom concepts
     :next-id-atom INITIAL_CONCEPT_NUM})))

(comment
  ;; Handy utility for future dev work
  (def db (get-in user/system [:apps :metadata-db :db]))

  (def test-file (slurp (clojure.java.io/resource "sample_tool.json")))
  (def parsed (cheshire.core/parse-string test-file true))
  (def my-concept {:concept-type :generic :provider-id "PROV6" :metadata parsed :revision-id 1})
  (def my-concept (assoc my-concept :concept-id (generate-concept-id db my-concept)))
  (save-concept db "PROV6" my-concept)
  ;; to view most recently saved concept
  (first @(:concepts-atom db))
  (get-concept-id db :order-option {:provider-id "PROV1"} "order-option-1")
  (get-concept db :order-option {:provider-id "PROV1"} "OO1200000001-PROV1"))
