(ns cmr.indexer.data.index-set
  (:refer-clojure :exclude [update])
  (:require [cmr.common.lifecycle :as lifecycle]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as cs]
            [cmr.transmit.metadata-db :as meta-db]
            [cmr.transmit.index-set :as index-set]
            [cmr.transmit.config :as transmit-config]
            [cmr.transmit.connection :as transmit-conn]
            [cmr.elastic-utils.index-util :as m :refer [defmapping defnestedmapping]]
            [cmr.common.cache :as cache]
            [cmr.common.config :as cfg]))

;; The number of shards to use for the collections index, the granule indexes containing granules
;; for a single collection, and the granule index containing granules for the remaining collections
;; can all be configured separately.
(def elastic-collection-index-num-shards (cfg/config-value-fn :elastic-collection-index-num-shards 5 #(Long. %)))
(def elastic-granule-index-num-shards (cfg/config-value-fn :elastic-granule-index-num-shards 5 #(Long. %)))
(def elastic-small-collections-index-num-shards (cfg/config-value-fn :elastic-small-collections-index-num-shards 20 #(Long. %)))
;; The number of shards to use for the tags index
(def elastic-tag-index-num-shards (cfg/config-value-fn :elastic-tag-index-num-shards 5 #(Long. %)))

(def collection-setting {:index
                         {:number_of_shards (elastic-collection-index-num-shards),
                          :number_of_replicas 1,
                          :refresh_interval "1s"}})

(def tag-setting {:index
                  {:number_of_shards (elastic-tag-index-num-shards)
                   :number_of_replicas 1,
                   :refresh_interval "1s"}})

(defnestedmapping attributes-field-mapping
  "Defines mappings for attributes."
  {:name m/string-field-mapping
   :group m/string-field-mapping
   :group.lowercase m/string-field-mapping
   :string-value m/string-field-mapping
   :string-value.lowercase m/string-field-mapping
   :float-value m/double-field-mapping
   :int-value m/int-field-mapping
   :datetime-value m/date-field-mapping
   :time-value m/date-field-mapping
   :date-value m/date-field-mapping})

(defnestedmapping science-keywords-field-mapping
  "Defines mappings for science keywords."
  {:category m/string-field-mapping
   :category.lowercase m/string-field-mapping
   :topic m/string-field-mapping
   :topic.lowercase m/string-field-mapping
   :term m/string-field-mapping
   :term.lowercase m/string-field-mapping
   :variable-level-1 m/string-field-mapping
   :variable-level-1.lowercase m/string-field-mapping
   :variable-level-2 m/string-field-mapping
   :variable-level-2.lowercase m/string-field-mapping
   :variable-level-3 m/string-field-mapping
   :variable-level-3.lowercase m/string-field-mapping
   :detailed-variable m/string-field-mapping
   :detailed-variable.lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid.lowercase m/string-field-mapping})

(defnestedmapping platform-hierarchical-mapping
  "Defines hierarchical mappings for platforms."
  {:category m/string-field-mapping
   :category.lowercase m/string-field-mapping
   :series-entity m/string-field-mapping
   :series-entity.lowercase m/string-field-mapping
   :short-name m/string-field-mapping
   :short-name.lowercase m/string-field-mapping
   :long-name m/string-field-mapping
   :long-name.lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid.lowercase m/string-field-mapping})

(defnestedmapping instrument-hierarchical-mapping
  "Defines hierarchical mappings for instruments."
  {:category m/string-field-mapping
   :category.lowercase m/string-field-mapping
   :class m/string-field-mapping
   :class.lowercase m/string-field-mapping
   :type m/string-field-mapping
   :type.lowercase m/string-field-mapping
   :subtype m/string-field-mapping
   :subtype.lowercase m/string-field-mapping
   :short-name m/string-field-mapping
   :short-name.lowercase m/string-field-mapping
   :long-name m/string-field-mapping
   :long-name.lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid.lowercase m/string-field-mapping})

(defnestedmapping data-center-hierarchical-mapping
  "Defines hierarchical mappings for any type of data center."
  {:level-0 m/string-field-mapping
   :level-0.lowercase m/string-field-mapping
   :level-1 m/string-field-mapping
   :level-1.lowercase m/string-field-mapping
   :level-2 m/string-field-mapping
   :level-2.lowercase m/string-field-mapping
   :level-3 m/string-field-mapping
   :level-3.lowercase m/string-field-mapping
   :short-name m/string-field-mapping
   :short-name.lowercase m/string-field-mapping
   :long-name m/string-field-mapping
   :long-name.lowercase m/string-field-mapping
   :url m/string-field-mapping
   :url.lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid.lowercase m/string-field-mapping})

(defnestedmapping orbit-calculated-spatial-domain-mapping
  "Defines mappings for storing orbit calculated spatial domains."
  {:orbital-model-name m/string-field-mapping
   :orbit-number m/int-field-mapping
   :start-orbit-number m/double-field-mapping
   :stop-orbit-number m/double-field-mapping
   :equator-crossing-longitude m/double-field-mapping
   :equator-crossing-date-time m/date-field-mapping})

(def spatial-coverage-fields
  "Defines the sets of fields shared by collections and granules for indexing spatial data."
  {;; Minimum Bounding Rectangle Fields
   ;; If a granule has multiple shapes then the MBR will cover all of the shapes
   :mbr-west m/float-field-mapping
   :mbr-north m/float-field-mapping
   :mbr-east m/float-field-mapping
   :mbr-south m/float-field-mapping
   :mbr-crosses-antimeridian m/bool-field-mapping

   ;; Largest Interior Rectangle Fields
   ;; If a granule has multiple shapes then the LR will be the largest in one of the shapes
   :lr-west m/float-field-mapping
   :lr-north m/float-field-mapping
   :lr-east m/float-field-mapping
   :lr-south m/float-field-mapping
   :lr-crosses-antimeridian m/bool-field-mapping

   ;; ords-info contains tuples of shapes stored in ords
   ;; Each tuple contains the shape type and the number of ordinates
   :ords-info (m/not-indexed (m/stored m/int-field-mapping))
   ;; ords contains longitude latitude pairs (ordinates) of all the shapes
   :ords (m/not-indexed (m/stored m/int-field-mapping))})

(defmapping collection-mapping :collection
  "Defines the elasticsearch mapping for storing collections"
  {:_id {:index "not_analyzed"
         :store true}}
  (merge {:deleted (m/stored m/bool-field-mapping) ; deleted=true is a tombstone
          :native-id (m/stored m/string-field-mapping)
          :native-id.lowercase m/string-field-mapping
          :user-id (m/stored m/string-field-mapping)

          ;; This comes from the metadata db column of the same name
          ;; and is by default equal to the Oracle system time at the
          ;; time the revision record is written

          ;; revision-date needs to be stored but you can't update an
          ;; existing mapping to be stored. We'll switch to revision-date2
          ;; and deprecate and then remove revision-date in sprint 32 or
          ;; later.
          :revision-date m/date-field-mapping
          :revision-date2 (m/stored m/date-field-mapping)

          :permitted-group-ids (m/stored m/string-field-mapping)
          :concept-id   (m/stored m/string-field-mapping)
          :revision-id (m/stored m/int-field-mapping)
          ;; This is used explicitly for sorting. The values take up less space in the
          ;; fielddata cache.
          :concept-seq-id m/int-field-mapping
          :entry-id           (m/stored m/string-field-mapping)
          :entry-id.lowercase m/string-field-mapping
          :entry-title           (m/stored m/string-field-mapping)
          :entry-title.lowercase m/string-field-mapping
          :provider-id           (m/stored m/string-field-mapping)
          :provider-id.lowercase m/string-field-mapping
          :short-name            (m/stored m/string-field-mapping)
          :short-name.lowercase  m/string-field-mapping
          :version-id            (m/stored m/string-field-mapping)
          :version-id.lowercase  m/string-field-mapping

          ;; Stored to allow retrieval for implementing granule acls
          :access-value                   (m/stored m/float-field-mapping)
          :processing-level-id            (m/stored m/string-field-mapping)
          :processing-level-id.lowercase  m/string-field-mapping
          :collection-data-type           (m/stored m/string-field-mapping)
          :collection-data-type.lowercase m/string-field-mapping
          :start-date                     (m/stored m/date-field-mapping)
          :end-date                       (m/stored m/date-field-mapping)
          :platform-sn                    m/string-field-mapping
          :platform-sn.lowercase          m/string-field-mapping
          :instrument-sn                  m/string-field-mapping
          :instrument-sn.lowercase        m/string-field-mapping
          :sensor-sn                      m/string-field-mapping
          :sensor-sn.lowercase            m/string-field-mapping
          :project-sn2                    (m/stored m/string-field-mapping)
          :project-sn2.lowercase          m/string-field-mapping
          :archive-center                 (m/stored m/string-field-mapping)
          :archive-center.lowercase       m/string-field-mapping
          :data-center                    (m/stored m/string-field-mapping)
          :data-center.lowercase          m/string-field-mapping
          :spatial-keyword                m/string-field-mapping
          :spatial-keyword.lowercase      m/string-field-mapping
          :two-d-coord-name               m/string-field-mapping
          :two-d-coord-name.lowercase     m/string-field-mapping
          :attributes                     attributes-field-mapping
          :downloadable                   (m/stored m/bool-field-mapping)

          ;; Mappings for nested fields used for searching and
          ;; hierarchical facets
          :science-keywords science-keywords-field-mapping
          :platforms platform-hierarchical-mapping
          :instruments instrument-hierarchical-mapping
          :archive-centers data-center-hierarchical-mapping
          ;; Contains all four types of data centers combined - archive,
          ;; centers, distribution centers, processing centers, and
          ;; originating centers.
          :data-centers data-center-hierarchical-mapping

          ;; Facet fields
          ;; We can run aggregations on the above science keywords as a
          ;; nested document. However the counts that come back are counts
          ;; of the nested documents. We want counts of collections for each
          ;; value so we must also capture the values at the parent level.
          :category m/string-field-mapping
          :topic m/string-field-mapping
          :term m/string-field-mapping
          :variable-level-1 m/string-field-mapping
          :variable-level-2 m/string-field-mapping
          :variable-level-3 m/string-field-mapping
          :detailed-variable m/string-field-mapping

          ;; mappings added for atom
          :browsable (m/stored m/bool-field-mapping)
          :atom-links (m/not-indexed (m/stored m/string-field-mapping))
          :summary (m/not-indexed (m/stored m/string-field-mapping))
          :metadata-format (m/not-indexed (m/stored m/string-field-mapping))
          :update-time (m/not-indexed (m/stored m/string-field-mapping))
          :associated-difs (m/stored m/string-field-mapping)
          :associated-difs.lowercase m/string-field-mapping
          :coordinate-system (m/not-indexed (m/stored m/string-field-mapping))

          ;; mappings added for opendata
          :insert-time (m/not-indexed (m/stored m/string-field-mapping))
          ;; This field contains multiple values obtained by
          ;; concatenating the category, topic, and term from
          ;; each science keyword. It represents the 'keywords'
          ;; field in the opendata format.
          :science-keywords-flat (m/stored m/string-field-mapping)
          :related-urls (m/stored m/string-field-mapping)
          :contact-email (m/stored m/string-field-mapping)
          :personnel (m/stored m/string-field-mapping)

          ;; analyzed field for keyword searches
          :keyword m/text-field-mapping
          :long-name.lowercase m/string-field-mapping
          :project-ln.lowercase m/string-field-mapping
          :platform-ln.lowercase m/string-field-mapping
          :instrument-ln.lowercase m/string-field-mapping
          :sensor-ln.lowercase m/string-field-mapping
          :temporal-keyword.lowercase m/string-field-mapping

          ;; orbit parameters
          :swath-width (m/stored m/double-field-mapping)
          :period (m/stored m/double-field-mapping)
          :inclination-angle (m/stored m/double-field-mapping)
          :number-of-orbits (m/stored m/double-field-mapping)
          :start-circular-latitude (m/stored m/double-field-mapping)}
         spatial-coverage-fields))

(def granule-settings-for-individual-indexes
  {:index {:number_of_shards (elastic-granule-index-num-shards),
           :number_of_replicas 1,
           :refresh_interval "1s"}})

(def granule-settings-for-small-collections-index
  {:index {:number_of_shards (elastic-small-collections-index-num-shards),
           :number_of_replicas 1,
           :refresh_interval "1s"}})

(defmapping granule-mapping :granule
  "Defines the elasticsearch mapping for storing collections"
  {:_id  {:path "concept-id"}}
  (merge
   {:concept-id (m/stored m/string-field-mapping)

    ;; This is used explicitly for sorting. The values take up less space in the
    ;; fielddata cache.
    :concept-seq-id m/int-field-mapping

    :collection-concept-id (m/stored m/string-field-mapping)

    ;; Used for aggregations. It takes up less space in the field data cache.
    :collection-concept-seq-id m/int-field-mapping

    ;; fields added for atom
    :entry-title (m/not-indexed (m/stored m/string-field-mapping))
    :metadata-format (m/not-indexed (m/stored m/string-field-mapping))
    :update-time (m/not-indexed (m/stored m/string-field-mapping))
    :coordinate-system (m/not-indexed (m/stored m/string-field-mapping))

    ;; Collection fields added strictly for sorting granule results
    :entry-title.lowercase m/string-field-mapping
    :short-name.lowercase  m/string-field-mapping
    :version-id.lowercase  m/string-field-mapping

    :provider-id           (m/stored m/string-field-mapping)
    :provider-id.lowercase m/string-field-mapping

    :granule-ur            (m/stored m/string-field-mapping)

    ;; Modified mappings for the lowercase fields for granule-ur, producer-gran-id,
    ;; and readable-granule-name-sort in order to prevent these values from being
    ;; stored in the elasticsearch field data cache (by specifying to use doc-values
    ;; for these fields). These 3 fields were taking more than 40% of the cache and
    ;; are rarely used to sort on.
    ;;
    ;; The convention used is to append a 2 to the name of the fields. Note that
    ;; for the search application to use the special lowercase2 fields, the fields
    ;; need to be mapped in cmr.search.data.query-to-elastic/field->lowercase-field.
    :granule-ur.lowercase2 (m/doc-values m/string-field-mapping)
    :producer-gran-id (m/stored m/string-field-mapping)
    :producer-gran-id.lowercase2 (m/doc-values m/string-field-mapping)

    :day-night (m/stored m/string-field-mapping)
    :day-night.lowercase m/string-field-mapping

    ;; Access value is stored to allow us to enforce acls after retrieving results
    ;; for certain types of queries.
    :access-value (m/stored m/float-field-mapping)

    ;; We need to sort by a combination of producer granule and granule ur
    ;; It should use producer granule id if present otherwise the granule ur is used
    ;; The producer granule id will be put in this field if present otherwise it
    ;; will default to granule-ur. This avoids the solution Catalog REST uses which is
    ;; to use a sort script which is (most likely) much slower.
    :readable-granule-name-sort2 (m/doc-values m/string-field-mapping)


    :platform-sn           m/string-field-mapping
    :platform-sn.lowercase m/string-field-mapping
    :instrument-sn         m/string-field-mapping
    :instrument-sn.lowercase m/string-field-mapping
    :sensor-sn             m/string-field-mapping
    :sensor-sn.lowercase   m/string-field-mapping
    :start-date (m/stored m/date-field-mapping)
    :end-date (m/stored m/date-field-mapping)
    :size (m/stored m/float-field-mapping)
    :cloud-cover (m/stored m/float-field-mapping)
    :orbit-calculated-spatial-domains orbit-calculated-spatial-domain-mapping
    :project-refs m/string-field-mapping
    :project-refs.lowercase m/string-field-mapping
    :revision-date         m/date-field-mapping
    :downloadable (m/stored m/bool-field-mapping)
    :browsable (m/stored m/bool-field-mapping)
    :attributes attributes-field-mapping
    :two-d-coord-name m/string-field-mapping
    :two-d-coord-name.lowercase m/string-field-mapping
    :start-coordinate-1 m/double-field-mapping
    :end-coordinate-1 m/double-field-mapping
    :start-coordinate-2 m/double-field-mapping
    :end-coordinate-2 m/double-field-mapping

    ;; Used for orbit search
    :orbit-asc-crossing-lon (m/stored m/double-field-mapping)
    :orbit-start-clat m/double-field-mapping
    :orbit-end-clat m/double-field-mapping
    :start-lat (m/stored m/double-field-mapping)
    :start-direction (m/stored m/string-field-mapping)
    :end-lat (m/stored m/double-field-mapping)
    :end-direction (m/stored m/string-field-mapping)

    ;; atom-links is a json string that contains the atom-links, which is a list of
    ;; maps of atom link attributes. We tried to use nested document to save atom-links
    ;; as a structure in elasticsearch, but can't find a way to retrieve it out.
    ;; So we are saving the links in json string, then parse it out when we need it.
    :atom-links (m/not-indexed (m/stored m/string-field-mapping))

    ;; :orbit-calculated-spatial-domains-json is json string
    ;; stored for retrieval similar to :atom-links above
    :orbit-calculated-spatial-domains-json (m/not-indexed (m/stored m/string-field-mapping))}

   spatial-coverage-fields))

(defmapping tag-mapping :tag
  "Defines the elasticsearch mapping for storing tags."
  {:_id  {:path "concept-id"}}
  {:concept-id (m/stored m/string-field-mapping)
   :namespace (m/stored m/string-field-mapping)
   :namespace.lowercase m/string-field-mapping
   :category (m/stored m/string-field-mapping)
   :category.lowercase m/string-field-mapping
   :value (m/stored m/string-field-mapping)
   :value.lowercase m/string-field-mapping
   :description (m/not-indexed (m/stored m/string-field-mapping))
   :originator-id.lowercase (m/stored m/string-field-mapping)
   ;; set of concept-ids stored as EDN gzipped and base64 encoded for retrieving purpose
   :associated-concept-ids-gzip-b64 (m/not-indexed (m/stored m/string-field-mapping))
   ;; for searching purpose
   :associated-concept-ids m/string-field-mapping})

(defn index-set
  "Returns the index-set configuration"
  [context]
  (let [colls-w-separate-indexes ((get-in context [:system :colls-with-separate-indexes-fn]))
        granule-indices (remove empty? colls-w-separate-indexes)]
    {:index-set {:name "cmr-base-index-set"
                 :id 1
                 :create-reason "indexer app requires this index set"
                 :collection {:indexes
                              [;; This index contains the latest revision of each collection and
                               ;; is used for normal searches.
                               {:name "collections"
                                :settings collection-setting}
                               ;; This index contains all the revisions (including tombstones) and
                               ;; is used for all-revisions searches.
                               {:name "all-collection-revisions"
                                :settings collection-setting}]
                              :mapping collection-mapping}
                 :granule {:indexes
                           (cons {:name "small_collections"
                                  :settings granule-settings-for-small-collections-index}
                                 (map (fn [collection]
                                        {:name collection
                                         :settings granule-settings-for-individual-indexes})
                                      granule-indices))
                           :mapping granule-mapping}
                 :tag {:indexes
                       [{:name "tags"
                         :settings tag-setting}]
                       :mapping tag-mapping}}}))

(defn reset
  "Reset configured elastic indexes"
  [context]
  (let [index-set-root-url (transmit-conn/root-url
                             (transmit-config/context->app-connection context :index-set))
        index-set-reset-url (format "%s/reset" index-set-root-url)]
    (client/request
      {:method :post
       :url index-set-reset-url
       :content-type :json
       :headers {transmit-config/token-header (transmit-config/echo-system-token)}
       :accept :json})))

(defn create
  "Submit a request to create index-set"
  [context index-set]
  (let [index-set-root-url (transmit-conn/root-url
                             (transmit-config/context->app-connection context :index-set))
        index-set-url (format "%s/index-sets" index-set-root-url)
        response (client/request
                   {:method :post
                    :url index-set-url
                    :body (cheshire/generate-string index-set)
                    :content-type :json
                    :accept :json
                    :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                    :throw-exceptions false})
        status (:status response)]
    (when-not (= 201 status)
      (errors/internal-error! (format "Failed to create index-set: %s, errors: %s"
                                      (pr-str index-set)
                                      (:body response))))))

(defn update
  "Submit a request to update an index-set"
  [context index-set]
  (let [index-set-root-url (transmit-conn/root-url
                             (transmit-config/context->app-connection context :index-set))
        index-set-url (format "%s/index-sets/%s" index-set-root-url (get-in index-set [:index-set :id]))
        response (client/request
                   {:method :put
                    :url index-set-url
                    :body (cheshire/generate-string index-set)
                    :content-type :json
                    :accept :json
                    :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                    :throw-exceptions false})
        status (:status response)]
    (when-not (= 200 status)
      (errors/internal-error! (format "Failed to create index-set: %s, errors: %s"
                                      (pr-str index-set)
                                      (:body response))))))

(defn fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  ([context]
   (let [index-set-id (get-in (index-set context) [:index-set :id])]
     (fetch-concept-type-index-names context index-set-id)))
  ([context index-set-id]
   (let [fetched-index-set (index-set/get-index-set context index-set-id)]
     (get-in fetched-index-set [:index-set :concepts]))))

(defn fetch-concept-mapping-types
  "Fetch mapping types for each concept type from index-set app"
  ([context]
   (let [index-set-id (get-in (index-set context) [:index-set :id])]
     (fetch-concept-mapping-types context index-set-id)))
  ([context index-set-id]
   (let [fetched-index-set (index-set/get-index-set context index-set-id)]
     {:collection (name (first (keys (get-in fetched-index-set [:index-set :collection :mapping]))))
      :granule (name (first (keys (get-in fetched-index-set [:index-set :granule :mapping]))))
      :tag (name (first (keys (get-in fetched-index-set [:index-set :tag :mapping]))))})))

(defn get-concept-type-index-names
  "Fetch index names associated with concepts."
  [context]
  (let [cache (cache/context->cache context cache/general-cache-key)]
    (cache/get-value cache :concept-indices (partial fetch-concept-type-index-names context))))

(defn get-concept-index-name
  "Return the concept index name for the given concept id"
  ([context concept-id revision-id all-revisions-index?]
   (let [concept-type (cs/concept-id->type concept-id)
         concept (when (= :granule concept-type) (meta-db/get-concept context concept-id revision-id))]
     (get-concept-index-name context concept-id revision-id all-revisions-index? concept)))
  ([context concept-id revision-id all-revisions-index? concept]
   (let [concept-type (cs/concept-id->type concept-id)
         indexes (get (get-concept-type-index-names context) concept-type)]
     (case concept-type
       :collection
       (get indexes (if all-revisions-index? :all-collection-revisions :collections))
       :tag
       (get indexes :tags)
       :granule
       (let [coll-concept-id (:parent-collection-id (:extra-fields concept))]
         (get indexes (keyword coll-concept-id) (get indexes :small_collections)))))))

(defn get-granule-index-name-for-collection
  "Return the granule index name for the input collection concept id"
  [context coll-concept-id]
  (let [indexes (get (get-concept-type-index-names context) :granule)]
    (get indexes (keyword coll-concept-id) (get indexes :small_collections))))

(defn get-granule-index-names-for-provider
  "Return the granule index names for the input provider id"
  [context provider-id]
  (let [indexes (get (get-concept-type-index-names context) :granule)
        filter-fn (fn [[k v]]
                    (or
                      (.endsWith (name k) (str "_" provider-id))
                      (= :small_collections k)))]
    (map second (filter filter-fn indexes))))

(defn get-concept-mapping-types
  "Fetch mapping types associated with concepts."
  [context]
  (let [cache (cache/context->cache context cache/general-cache-key)]
    (cache/get-value cache :concept-mapping-types (partial fetch-concept-mapping-types context))))
