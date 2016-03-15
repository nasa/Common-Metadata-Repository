(ns cmr.access-control.data.access-control-index
  "Performs search and indexing of access control data."
  (:require [cmr.elastic-utils.index-util :as m :refer [defmapping]]
            [cmr.common-app.services.search.elastic-search-index :as esi]
            [cmr.common-app.services.search.query-to-elastic :as q2e]
            [cmr.common.lifecycle :as l]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(def ^:private group-index-name
  "The name of the index in elastic search."
  "groups")

(def ^:private group-type-name
  "The name of the mapping type within the cubby elasticsearch index."
  "access-group")

(defmapping ^:private group-mappings group-type-name
  "Defines the field mappings and type options for indexing groups in elasticsearch."
  {:concept-id (m/stored m/string-field-mapping)
   :revision-id (m/stored m/int-field-mapping)

   :name (m/stored m/string-field-mapping)
   :name.lowercase m/string-field-mapping

   :provider-id (m/stored m/string-field-mapping)
   :provider-id.lowercase m/string-field-mapping

   :description (m/not-indexed (m/stored m/string-field-mapping))

   :legacy-guid (m/stored m/string-field-mapping)

   ;; Member search is always case insensitive
   :members.lowercase m/string-field-mapping
   ;; Member count is returned in the group response. The list of members is returned separately so
   ;; we don't store the members in the elastic index. If members end up being stored at some point
   ;; we can get rid of this field.
   :member-count (m/stored (m/not-indexed m/int-field-mapping))})

(def ^:private group-index-settings
  "Defines the elasticsearch index settings."
  {:number_of_shards 3,
   :number_of_replicas 1,
   :refresh_interval "1s"})

(defn create-index-or-update-mappings
  "Creates the index needed in Elasticsearch for data storage"
  [elastic-store]
  (m/create-index-or-update-mappings
    group-index-name group-index-settings group-type-name group-mappings elastic-store))

(defn reset
  "Deletes all data from the index"
  [elastic-store]
  (m/reset group-index-name group-index-settings group-type-name group-mappings elastic-store))

(defmulti index-concept
  "Indexes the concept map in elastic search."
  (fn [context concept-map]
    (:concept-type concept-map)))

(defmulti delete-concept
  "Deletes the concept map in elastic search."
  (fn [context concept-map]
    (:concept-type concept-map)))

(defn- safe-lowercase
  [v]
  (when v (str/lower-case v)))

(defn- group-concept-map->elastic-doc
  "Converts a concept map containing an access group into the elasticsearch document to index."
  [concept-map]
  (let [group (edn/read-string (:metadata concept-map))]
    (-> group
        (merge (select-keys concept-map [:concept-id :revision-id]))
        (assoc :name.lowercase (safe-lowercase (:name group))
               :provider-id.lowercase (safe-lowercase (:provider-id group))
               :members.lowercase (map str/lower-case (:members group))
               :member-count (count (:members group)))
        (dissoc :members))))

(defmethod index-concept :access-group
  [context concept-map]
  (let [elastic-doc (group-concept-map->elastic-doc concept-map)
        {:keys [concept-id revision-id]} concept-map
        elastic-store (esi/context->search-index context)]
    (m/save-elastic-doc
     elastic-store group-index-name group-type-name concept-id elastic-doc revision-id
     {:ignore-conflict? true})))

(defmethod delete-concept :access-group
  [context concept-map]
  (let [id (:concept-id concept-map)]
    (m/delete-by-id (esi/context->search-index context)
                       group-index-name
                       group-type-name
                       id)))

(defn delete-provider-groups
  "Unindexes all access groups owned by provider-id."
  [context provider-id]
  (m/delete-by-query (esi/context->search-index context)
                     group-index-name
                     group-type-name
                     ;; only :provider-id.lowercase is indexed, so to find the access group by
                     ;; provider-id we need to compare the lowercased version
                     {:term {:provider-id.lowercase (.toLowerCase provider-id)}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search Functions

(defmethod q2e/concept-type->field-mappings :access-group
  [_]
  {:provider :provider-id})

(defmethod q2e/field->lowercase-field-mappings :access-group
  [_]
  {:provider "provider-id.lowercase"
   :member "members.lowercase"})

(defmethod esi/concept-type->index-info :access-group
  [context _ _]
  {:index-name group-index-name
   :type-name group-type-name})
