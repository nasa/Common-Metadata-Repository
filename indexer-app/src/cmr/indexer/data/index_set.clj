(ns cmr.indexer.data.index-set
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
            [cmr.common.cache :as cache]
            [cmr.common.config :as cfg]
            [cmr.system-trace.core :refer [deftracefn]]))

;; The number of shards to use for the collections index, the granule indexes containing granules
;; for a single collection, and the granule index containing granules for the remaining collections
;; can all be configured separately.
(def elastic-collection-index-num-shards (cfg/config-value-fn :elastic-collection-index-num-shards 5 #(Long. %)))
(def elastic-granule-index-num-shards (cfg/config-value-fn :elastic-granule-index-num-shards 5 #(Long. %)))
(def elastic-small-collections-index-num-shards (cfg/config-value-fn :elastic-small-collections-index-num-shards 20 #(Long. %)))

(def collection-setting {:index
                         {:number_of_shards (elastic-collection-index-num-shards),
                          :number_of_replicas 1,
                          :refresh_interval "1s"}})



(def string-field-mapping
  {:type "string" :index "not_analyzed"})

(def text-field-mapping
  "Used for analyzed text fields"
  {:type "string"
   ; these fields will be split into multiple terms using the analyzer
   :index "analyzed"
   ; Norms are metrics about fields that elastic can use to weigh certian fields more than
   ; others when computing a document relevance. A typical example is field length - short
   ; fields are weighted more heavily than long feilds. We don't need them for scoring.
   :omit_norms "true"
   ; split the text on whitespace, but don't do any stemmming, etc.
   :analyzer "whitespace"
   ; Don't bother storing term positions or term frequencies in this field
   :index_options "docs"})

(def date-field-mapping
  {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"})

(def double-field-mapping
  {:type "double"})

(def float-field-mapping
  {:type "float"})

(def int-field-mapping
  {:type "integer"})

(def bool-field-mapping
  {:type "boolean"})

(defn stored
  "modifies a mapping to indicate that it should be stored"
  [field-mapping]
  (assoc field-mapping :store "yes"))

(defn not-indexed
  "modifies a mapping to indicate that it should not be indexed and thus is not searchable."
  [field-mapping]
  (assoc field-mapping :index "no"))

(defn doc-values
  "Modifies a mapping to indicate that it should use doc values instead of the field data cache
  for this field.  The tradeoff is slightly slower performance, but the field no longer takes
  up memory in the field data cache.  Only use doc values for fields which require a large
  amount of memory and are not frequently used for sorting."
  [field-mapping]
  (assoc field-mapping :doc_values true))

(def attributes-field-mapping
  "Defines mappings for attributes."
  {:type "nested"
   :dynamic "strict"
   :_source {:enabled false}
   :_all {:enabled false}
   :properties
   {:name string-field-mapping
    :string-value string-field-mapping
    :string-value.lowercase string-field-mapping
    :float-value double-field-mapping
    :int-value int-field-mapping
    :datetime-value date-field-mapping
    :time-value date-field-mapping
    :date-value date-field-mapping}})

(def science-keywords-field-mapping
  "Defines mappings for science keywords."
  {:type "nested"
   :dynamic "strict"
   :_source {:enabled false}
   :_all {:enabled false}
   :properties
   {:category string-field-mapping
    :category.lowercase string-field-mapping
    :topic string-field-mapping
    :topic.lowercase string-field-mapping
    :term string-field-mapping
    :term.lowercase string-field-mapping
    :variable-level-1 string-field-mapping
    :variable-level-1.lowercase string-field-mapping
    :variable-level-2 string-field-mapping
    :variable-level-2.lowercase string-field-mapping
    :variable-level-3 string-field-mapping
    :variable-level-3.lowercase string-field-mapping
    :detailed-variable string-field-mapping
    :detailed-variable.lowercase string-field-mapping}})

(def platform-hierarchical-mapping
  "Defines hierarchical mappings for platforms."
  {:type "nested"
   :dynamic "strict"
   :_source {:enabled false}
   :_all {:enabled false}
   :properties
   {:category string-field-mapping
    :category.lowercase string-field-mapping
    :series-entity string-field-mapping
    :series-entity.lowercase string-field-mapping
    :short-name string-field-mapping
    :short-name.lowercase string-field-mapping
    :long-name string-field-mapping
    :long-name.lowercase string-field-mapping
    :uuid string-field-mapping
    :uuid.lowercase string-field-mapping}})

(def instrument-hierarchical-mapping
  "Defines hierarchical mappings for instruments."
  {:type "nested"
   :dynamic "strict"
   :_source {:enabled false}
   :_all {:enabled false}
   :properties
   {:category string-field-mapping
    :category.lowercase string-field-mapping
    :class string-field-mapping
    :class.lowercase string-field-mapping
    :type string-field-mapping
    :type.lowercase string-field-mapping
    :subtype string-field-mapping
    :subtype.lowercase string-field-mapping
    :short-name string-field-mapping
    :short-name.lowercase string-field-mapping
    :long-name string-field-mapping
    :long-name.lowercase string-field-mapping
    :uuid string-field-mapping
    :uuid.lowercase string-field-mapping}})

(def orbit-calculated-spatial-domain-mapping
  {:type "nested"
   :dynamic "strict"
   :_source {:enabled false}
   :_all {:enabled false}
   :properties {:orbital-model-name string-field-mapping
                :orbit-number int-field-mapping
                :start-orbit-number double-field-mapping
                :stop-orbit-number double-field-mapping
                :equator-crossing-longitude double-field-mapping
                :equator-crossing-date-time date-field-mapping}})

(def spatial-coverage-fields
  "Defines the sets of fields shared by collections and granules for indexing spatial data."
  {;; Minimum Bounding Rectangle Fields
   ;; If a granule has multiple shapes then the MBR will cover all of the shapes
   :mbr-west float-field-mapping
   :mbr-north float-field-mapping
   :mbr-east float-field-mapping
   :mbr-south float-field-mapping
   :mbr-crosses-antimeridian bool-field-mapping

   ;; Largest Interior Rectangle Fields
   ;; If a granule has multiple shapes then the LR will be the largest in one of the shapes
   :lr-west float-field-mapping
   :lr-north float-field-mapping
   :lr-east float-field-mapping
   :lr-south float-field-mapping
   :lr-crosses-antimeridian bool-field-mapping

   ;; ords-info contains tuples of shapes stored in ords
   ;; Each tuple contains the shape type and the number of ordinates
   :ords-info (not-indexed (stored int-field-mapping))
   ;; ords contains longitude latitude pairs (ordinates) of all the shapes
   :ords (not-indexed (stored int-field-mapping))})

(def collection-mapping
  {:collection {:dynamic "strict",
                :_source {:enabled false},
                :_all {:enabled false},
                :_id {
                  :index "not_analyzed"
                  :store true
                }
                :_ttl {:enabled true},
                :properties (merge {:deleted (stored bool-field-mapping) ; deleted=true is a tombstone
                                    :native-id (stored string-field-mapping)
                                    :native-id.lowercase string-field-mapping
                                    :user-id (stored string-field-mapping)

                                    ;; This comes from the metadata db column of the same name
                                    ;; and is by default equal to the Oracle system time at the
                                    ;; time the revision record is written

                                    ;; revision-date needs to be stored but you can't update an
                                    ;; existing mapping to be stored. We'll switch to revision-date2
                                    ;; and deprecate and then remove revision-date in sprint 32 or
                                    ;; later.
                                    :revision-date date-field-mapping
                                    :revision-date2 (stored date-field-mapping)

                                    :permitted-group-ids (stored string-field-mapping)
                                    :concept-id   (stored string-field-mapping)
                                    :revision-id (stored int-field-mapping)
                                    ;; This is used explicitly for sorting. The values take up less space in the
                                    ;; fielddata cache.
                                    :concept-seq-id int-field-mapping
                                    :entry-id           (stored string-field-mapping)
                                    :entry-id.lowercase string-field-mapping
                                    :entry-title           (stored string-field-mapping)
                                    :entry-title.lowercase string-field-mapping
                                    :provider-id           (stored string-field-mapping)
                                    :provider-id.lowercase string-field-mapping
                                    :short-name            (stored string-field-mapping)
                                    :short-name.lowercase  string-field-mapping
                                    :version-id            (stored string-field-mapping)
                                    :version-id.lowercase  string-field-mapping

                                    ;; Stored to allow retrieval for implementing granule acls
                                    :access-value          (stored float-field-mapping)
                                    :processing-level-id   (stored string-field-mapping)
                                    :processing-level-id.lowercase string-field-mapping
                                    :collection-data-type  (stored string-field-mapping)
                                    :collection-data-type.lowercase string-field-mapping
                                    :start-date            (stored date-field-mapping)
                                    :end-date              (stored date-field-mapping)
                                    :platform-sn           string-field-mapping
                                    :platform-sn.lowercase string-field-mapping
                                    :instrument-sn           string-field-mapping
                                    :instrument-sn.lowercase string-field-mapping
                                    :sensor-sn             string-field-mapping
                                    :sensor-sn.lowercase   string-field-mapping
                                    :project-sn            (stored string-field-mapping)
                                    :project-sn.lowercase  string-field-mapping
                                    :archive-center        (stored string-field-mapping)
                                    :archive-center.lowercase string-field-mapping
                                    :spatial-keyword        string-field-mapping
                                    :spatial-keyword.lowercase string-field-mapping
                                    :two-d-coord-name string-field-mapping
                                    :two-d-coord-name.lowercase string-field-mapping
                                    :attributes attributes-field-mapping
                                    :downloadable (stored bool-field-mapping)

                                    ;; Mappings for nested fields used for searching and
                                    ;; hierarchical facets
                                    :science-keywords science-keywords-field-mapping
                                    :platforms platform-hierarchical-mapping
                                    :instruments instrument-hierarchical-mapping

                                    ;; Facet fields
                                    ;; We can run aggregations on the above science keywords as a
                                    ;; nested document. However the counts that come back are counts
                                    ;; of the nested documents. We want counts of collections for each
                                    ;; value so we must also capture the values at the parent level.
                                    :category string-field-mapping
                                    :topic string-field-mapping
                                    :term string-field-mapping
                                    :variable-level-1 string-field-mapping
                                    :variable-level-2 string-field-mapping
                                    :variable-level-3 string-field-mapping
                                    :detailed-variable string-field-mapping

                                    ;; mappings added for atom
                                    :browsable (stored bool-field-mapping)
                                    :atom-links (not-indexed (stored string-field-mapping))
                                    :summary (not-indexed (stored string-field-mapping))
                                    :metadata-format (not-indexed (stored string-field-mapping))
                                    :update-time (not-indexed (stored string-field-mapping))
                                    :associated-difs (stored string-field-mapping)
                                    :associated-difs.lowercase string-field-mapping
                                    :coordinate-system (not-indexed (stored string-field-mapping))

                                    ;; mappings added for opendata
                                    :insert-time (not-indexed (stored string-field-mapping))
                                    ;; This field contains multiple values obtained by
                                    ;; concatenating the category, topic, and term from
                                    ;; each science keyword. It represents the 'keywords'
                                    ;; field in the opendata format.
                                    :science-keywords-flat (stored string-field-mapping)
                                    :related-urls (stored string-field-mapping)
                                    :contact-email (stored string-field-mapping)
                                    :personnel (stored string-field-mapping)

                                    ;; analyzed field for keyword searches
                                    :keyword text-field-mapping
                                    :long-name.lowercase string-field-mapping
                                    :project-ln.lowercase string-field-mapping
                                    :platform-ln.lowercase string-field-mapping
                                    :instrument-ln.lowercase string-field-mapping
                                    :sensor-ln.lowercase string-field-mapping
                                    :temporal-keyword.lowercase string-field-mapping

                                    ;; orbit parameters
                                    :swath-width (stored double-field-mapping)
                                    :period (stored double-field-mapping)
                                    :inclination-angle (stored double-field-mapping)
                                    :number-of-orbits (stored double-field-mapping)
                                    :start-circular-latitude (stored double-field-mapping)}
                                   spatial-coverage-fields)}})

(def granule-settings-for-individual-indexes
  {:index {:number_of_shards (elastic-granule-index-num-shards),
           :number_of_replicas 1,
           :refresh_interval "1s"}})

(def granule-settings-for-small-collections-index
  {:index {:number_of_shards (elastic-small-collections-index-num-shards),
           :number_of_replicas 1,
           :refresh_interval "1s"}})

(def granule-mapping
  {:granule
   {:dynamic "strict",
    :_source {:enabled false},
    :_all {:enabled false},
    :_id  {:path "concept-id"},
    :_ttl {:enabled true},
    :properties (merge
                  {:concept-id (stored string-field-mapping)

                   ;; This is used explicitly for sorting. The values take up less space in the
                   ;; fielddata cache.
                   :concept-seq-id int-field-mapping

                   :collection-concept-id (stored string-field-mapping)

                   ;; Used for aggregations. It takes up less space in the field data cache.
                   :collection-concept-seq-id int-field-mapping

                   ;; fields added for atom
                   :entry-title (not-indexed (stored string-field-mapping))
                   :metadata-format (not-indexed (stored string-field-mapping))
                   :update-time (not-indexed (stored string-field-mapping))
                   :coordinate-system (not-indexed (stored string-field-mapping))

                   ;; Collection fields added strictly for sorting granule results
                   :entry-title.lowercase string-field-mapping
                   :short-name.lowercase  string-field-mapping
                   :version-id.lowercase  string-field-mapping

                   :provider-id           (stored string-field-mapping)
                   :provider-id.lowercase string-field-mapping

                   :granule-ur            (stored string-field-mapping)

                   ;; Modified mappings for the lowercase fields for granule-ur, producer-gran-id,
                   ;; and readable-granule-name-sort in order to prevent these values from being
                   ;; stored in the elasticsearch field data cache (by specifying to use doc-values
                   ;; for these fields). These 3 fields were taking more than 40% of the cache and
                   ;; are rarely used to sort on.
                   ;;
                   ;; The convention used is to append a 2 to the name of the fields. Note that
                   ;; for the search application to use the special lowercase2 fields, the fields
                   ;; need to be mapped in cmr.search.data.query-to-elastic/field->lowercase-field.
                   :granule-ur.lowercase2 (doc-values string-field-mapping)
                   :producer-gran-id (stored string-field-mapping)
                   :producer-gran-id.lowercase2 (doc-values string-field-mapping)

                   :day-night (stored string-field-mapping)
                   :day-night.lowercase string-field-mapping

                   ;; Access value is stored to allow us to enforce acls after retrieving results
                   ;; for certain types of queries.
                   :access-value (stored float-field-mapping)

                   ;; We need to sort by a combination of producer granule and granule ur
                   ;; It should use producer granule id if present otherwise the granule ur is used
                   ;; The producer granule id will be put in this field if present otherwise it
                   ;; will default to granule-ur. This avoids the solution Catalog REST uses which is
                   ;; to use a sort script which is (most likely) much slower.
                   :readable-granule-name-sort2 (doc-values string-field-mapping)


                   :platform-sn           string-field-mapping
                   :platform-sn.lowercase string-field-mapping
                   :instrument-sn         string-field-mapping
                   :instrument-sn.lowercase string-field-mapping
                   :sensor-sn             string-field-mapping
                   :sensor-sn.lowercase   string-field-mapping
                   :start-date (stored date-field-mapping)
                   :end-date (stored date-field-mapping)
                   :size (stored float-field-mapping)
                   :cloud-cover (stored float-field-mapping)
                   :orbit-calculated-spatial-domains orbit-calculated-spatial-domain-mapping
                   :project-refs string-field-mapping
                   :project-refs.lowercase string-field-mapping
                   :revision-date         date-field-mapping
                   :downloadable (stored bool-field-mapping)
                   :browsable (stored bool-field-mapping)
                   :attributes attributes-field-mapping
                   :two-d-coord-name string-field-mapping
                   :two-d-coord-name.lowercase string-field-mapping
                   :start-coordinate-1 double-field-mapping
                   :end-coordinate-1 double-field-mapping
                   :start-coordinate-2 double-field-mapping
                   :end-coordinate-2 double-field-mapping

                   ;; Used for orbit search
                   :orbit-asc-crossing-lon (stored double-field-mapping)
                   :orbit-start-clat double-field-mapping
                   :orbit-end-clat double-field-mapping
                   :start-lat (stored double-field-mapping)
                   :start-direction (stored string-field-mapping)
                   :end-lat (stored double-field-mapping)
                   :end-direction (stored string-field-mapping)

                   ;; atom-links is a json string that contains the atom-links, which is a list of
                   ;; maps of atom link attributes. We tried to use nested document to save atom-links
                   ;; as a structure in elasticsearch, but can't find a way to retrieve it out.
                   ;; So we are saving the links in json string, then parse it out when we need it.
                   :atom-links (not-indexed (stored string-field-mapping))

                   ;; :orbit-calculated-spatial-domains-json is json string
                   ;; stored for retrieval similar to :atom-links above
                   :orbit-calculated-spatial-domains-json (not-indexed (stored string-field-mapping))
                   }
                  spatial-coverage-fields)}})

(defn index-set
  "Returns the index-set configuration"
  [context]
  (let [colls-w-separate-indexes ((get-in context [:system :colls-with-separate-indexes-fn]))
        granule-indices (remove empty? colls-w-separate-indexes )]
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
                           :mapping granule-mapping}}}))

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
      :granule (name (first (keys (get-in fetched-index-set [:index-set :granule :mapping]))))})))

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
     (if (= :collection concept-type)
       (get indexes (if all-revisions-index? :all-collection-revisions :collections))
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
