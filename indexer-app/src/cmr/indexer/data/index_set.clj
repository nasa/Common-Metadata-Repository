(ns cmr.indexer.data.index-set
  (:refer-clojure :exclude [update])
  (:require
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as cs]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.generics :as common-generic]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.index-util :as m :refer [defmapping defnestedmapping]]
   [cmr.indexer.data.index-set-generics :as index-set-gen]
   [cmr.indexer.data.index-set-elasticsearch :as index-set-es]
   [cmr.transmit.metadata-db :as meta-db]))

;; The number of shards to use for the collections index, the granule indexes containing granules
;; for a single collection, and the granule index containing granules for the remaining collections
;; can all be configured separately.
(defconfig elastic-collection-index-num-shards
  "Number of shards to use for the collection index"
  {:default 5 :type Long})

(defconfig elastic-collection-v2-index-num-shards
  "Number of shards to use for the collection index"
  {:default 10 :type Long})

(defconfig elastic-granule-index-num-shards
  "Number of shards to use for the individual collection granule indexes."
  {:default 5 :type Long})

(defconfig elastic-small-collections-index-num-shards
  "Number of shards to use for the small collections granule index."
  {:default 20 :type Long})

(defconfig elastic-deleted-granule-index-num-shards
  "Number of shards to use for the deleted granules index."
  {:default 5 :type Long})

(defconfig elastic-tag-index-num-shards
  "Number of shards to use for the tags index."
  {:default 5 :type Long})

(defconfig elastic-variable-index-num-shards
  "Number of shards to use for the variables index."
  {:default 5 :type Long})

(defconfig elastic-service-index-num-shards
  "Number of shards to use for the services index."
  {:default 5 :type Long})

(defconfig elastic-tool-index-num-shards
  "Number of shards to use for the tools index."
  {:default 5 :type Long})

(defconfig elastic-autocomplete-index-num-shards
  "Number of shards to use for the autocomplete index"
  {:default 5 :type Long})

(defconfig elastic-subscription-index-num-shards
  "Number of shards to use for the subscriptions index"
  {:default 5 :type Long})

(defconfig collections-index-alias
  "The alias to use for the collections index."
  {:default "collection_search_alias" :type String})

(defconfig collections-index
  "The index to use for the latest collection revisions."
  {:default "1_collections_v2" :type String})

(def ^:private MAX_RESULT_WINDOW
  "Number of max results can be returned in an Elasticsearch query."
  1000000)

(def collection-setting-v1 {:index
                            {:number_of_shards (elastic-collection-index-num-shards)
                             :number_of_replicas 1,
                             :max_result_window MAX_RESULT_WINDOW,
                             :refresh_interval "1s"}})

(def collection-setting-v2 {:index
                            {:number_of_shards (elastic-collection-v2-index-num-shards),
                             :number_of_replicas 1,
                             :max_result_window MAX_RESULT_WINDOW,
                             :refresh_interval "1s"}})

(def tag-setting {:index
                  {:number_of_shards (elastic-tag-index-num-shards)
                   :number_of_replicas 1,
                   :max_result_window MAX_RESULT_WINDOW,
                   :refresh_interval "1s"}})

(def deleted-granule-setting {:index
                              {:number_of_shards (elastic-deleted-granule-index-num-shards)
                               :number_of_replicas 1,
                               :max_result_window MAX_RESULT_WINDOW,
                               :refresh_interval "1s"}})

(def variable-setting {:index
                       {:number_of_shards (elastic-variable-index-num-shards)
                        :number_of_replicas 1,
                        :max_result_window MAX_RESULT_WINDOW,
                        :refresh_interval "1s"}})

(def autocomplete-settings {:index
                            {:number_of_shards (elastic-autocomplete-index-num-shards)
                             :number_of_replicas 1
                             :refresh_interval "1s"}})

(def service-setting {:index
                       {:number_of_shards (elastic-service-index-num-shards)
                        :number_of_replicas 1,
                        :max_result_window MAX_RESULT_WINDOW,
                        :refresh_interval "1s"}})

(def tool-setting {:index
                    {:number_of_shards (elastic-tool-index-num-shards)
                     :number_of_replicas 1,
                     :refresh_interval "1s"}})

(def subscription-setting {:index
                            {:number_of_shards (elastic-subscription-index-num-shards)
                             :number_of_replicas 1,
                             :refresh_interval "1s"}})

(defnestedmapping attributes-field-mapping
  "Defines mappings for attributes."
  {:name m/string-field-mapping
   :group m/string-field-mapping
   :group-lowercase m/string-field-mapping
   :string-value m/string-field-mapping
   :string-value-lowercase m/string-field-mapping
   :float-value m/double-field-mapping
   :int-value m/int-field-mapping
   :datetime-value m/date-field-mapping
   :time-value m/date-field-mapping
   :date-value m/date-field-mapping})

(defnestedmapping science-keywords-field-mapping
  "Defines mappings for science keywords."
  {:category m/string-field-mapping
   :category-lowercase m/string-field-mapping
   :topic m/string-field-mapping
   :topic-lowercase m/string-field-mapping
   :term m/string-field-mapping
   :term-lowercase m/string-field-mapping
   :variable-level-1 m/string-field-mapping
   :variable-level-1-lowercase m/string-field-mapping
   :variable-level-2 m/string-field-mapping
   :variable-level-2-lowercase m/string-field-mapping
   :variable-level-3 m/string-field-mapping
   :variable-level-3-lowercase m/string-field-mapping
   :detailed-variable m/string-field-mapping
   :detailed-variable-lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid-lowercase m/string-field-mapping})

(defnestedmapping tag-associations-mapping
  "Defines mappings for tag associations."
  {:tag-key-lowercase m/string-field-mapping
   :originator-id-lowercase m/string-field-mapping
   :tag-value-lowercase m/string-field-mapping})

(defnestedmapping variables-mapping
  "Defines mappings for variables."
  {:measurement m/string-field-mapping
   :measurement-lowercase m/string-field-mapping
   :variable m/string-field-mapping
   :variable-lowercase m/string-field-mapping
   :originator-id-lowercase m/string-field-mapping})

;;DEPRECATED see :platforms2
(defnestedmapping platform-hierarchical-mapping
  "Defines hierarchical mappings for platforms."
  {:category m/string-field-mapping
   :category-lowercase m/string-field-mapping
   :series-entity m/string-field-mapping
   :series-entity-lowercase m/string-field-mapping
   :short-name m/string-field-mapping
   :short-name-lowercase m/string-field-mapping
   :long-name m/string-field-mapping
   :long-name-lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid-lowercase m/string-field-mapping})

(defnestedmapping platform2-hierarchical-mapping
  "Defines hierarchical mappings for platforms."
  {:basis m/string-field-mapping
   :basis-lowercase m/string-field-mapping
   :category m/string-field-mapping
   :category-lowercase m/string-field-mapping
   :sub-category m/string-field-mapping
   :sub-category-lowercase m/string-field-mapping
   :short-name m/string-field-mapping
   :short-name-lowercase m/string-field-mapping
   :long-name m/string-field-mapping
   :long-name-lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid-lowercase m/string-field-mapping})

(defnestedmapping instrument-hierarchical-mapping
  "Defines hierarchical mappings for instruments."
  {:category m/string-field-mapping
   :category-lowercase m/string-field-mapping
   :class m/string-field-mapping
   :class-lowercase m/string-field-mapping
   :type m/string-field-mapping
   :type-lowercase m/string-field-mapping
   :subtype m/string-field-mapping
   :subtype-lowercase m/string-field-mapping
   :short-name m/string-field-mapping
   :short-name-lowercase m/string-field-mapping
   :long-name m/string-field-mapping
   :long-name-lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid-lowercase m/string-field-mapping})

(defnestedmapping data-center-hierarchical-mapping
  "Defines hierarchical mappings for any type of data center."
  {:level-0 m/string-field-mapping
   :level-0-lowercase m/string-field-mapping
   :level-1 m/string-field-mapping
   :level-1-lowercase m/string-field-mapping
   :level-2 m/string-field-mapping
   :level-2-lowercase m/string-field-mapping
   :level-3 m/string-field-mapping
   :level-3-lowercase m/string-field-mapping
   :short-name m/string-field-mapping
   :short-name-lowercase m/string-field-mapping
   :long-name m/string-field-mapping
   :long-name-lowercase m/string-field-mapping
   :url m/string-field-mapping
   :url-lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid-lowercase m/string-field-mapping})

(defnestedmapping location-keywords-hierarchical-mapping
  "Defines hierarchical mappings for location keywords."
  {:category m/string-field-mapping
   :category-lowercase m/string-field-mapping
   :type m/string-field-mapping
   :type-lowercase m/string-field-mapping
   :subregion-1 m/string-field-mapping
   :subregion-1-lowercase m/string-field-mapping
   :subregion-2 m/string-field-mapping
   :subregion-2-lowercase m/string-field-mapping
   :subregion-3 m/string-field-mapping
   :subregion-3-lowercase m/string-field-mapping
   :detailed-location m/string-field-mapping
   :detailed-location-lowercase m/string-field-mapping
   :uuid m/string-field-mapping
   :uuid-lowercase m/string-field-mapping})

(defnestedmapping orbit-calculated-spatial-domain-mapping
  "Defines mappings for storing orbit calculated spatial domains."
  {:orbital-model-name m/string-field-mapping
   :orbit-number m/int-field-mapping
   :start-orbit-number m/double-field-mapping
   :stop-orbit-number m/double-field-mapping
   :equator-crossing-longitude m/double-field-mapping
   :equator-crossing-date-time m/date-field-mapping})

(defnestedmapping track-pass-mapping
  "Defines mappings for storing track pass."
  {:pass (m/doc-values m/int-field-mapping)
   :tiles (m/doc-values m/string-field-mapping)})

(defnestedmapping prioritized-humanizer-mapping
  "Defines a string value and priority for use in boosting facets."
  {:value m/string-field-mapping
   :value-lowercase m/string-field-mapping
   :priority m/int-field-mapping})

(defnestedmapping float-prioritized-mapping
  "Defines a float value and priority for use in boosting facets."
  {:value m/float-field-mapping
   :priority m/int-field-mapping})

(defnestedmapping temporal-mapping
  "Defines mappings for TemporalExtents."
  {:start-date m/date-field-mapping
   :end-date m/date-field-mapping})

(defnestedmapping measurement-identifiers-mapping
  "Defines mappings for variable measurement identifiers."
  {:contextmedium m/string-field-mapping
   :contextmedium-lowercase m/string-field-mapping
   :object m/string-field-mapping
   :object-lowercase m/string-field-mapping
   :quantity m/string-field-mapping
   :quantity-lowercase m/string-field-mapping})

(def spatial-coverage-fields
  "Defines the sets of fields shared by collections and granules for indexing spatial data."
  {;; Minimum Bounding Rectangle Fields
   ;; If a granule has multiple shapes then the MBR will cover all of the shapes
   :mbr-west m/float-field-mapping
   :mbr-north m/float-field-mapping
   :mbr-east m/float-field-mapping
   :mbr-south m/float-field-mapping

   :mbr-west-doc-values (m/doc-values m/float-field-mapping)
   :mbr-north-doc-values (m/doc-values m/float-field-mapping)
   :mbr-east-doc-values (m/doc-values m/float-field-mapping)
   :mbr-south-doc-values (m/doc-values m/float-field-mapping)

   :mbr-crosses-antimeridian m/bool-field-mapping

   ;; Largest Interior Rectangle Fields
   ;; If a granule has multiple shapes then the LR will be the largest in one of the shapes
   :lr-west m/float-field-mapping
   :lr-north m/float-field-mapping
   :lr-east m/float-field-mapping
   :lr-south m/float-field-mapping

   :lr-west-doc-values (m/doc-values m/float-field-mapping)
   :lr-north-doc-values (m/doc-values m/float-field-mapping)
   :lr-east-doc-values (m/doc-values m/float-field-mapping)
   :lr-south-doc-values (m/doc-values m/float-field-mapping)

   :lr-crosses-antimeridian m/bool-field-mapping

   ;; ords-info contains tuples of shapes stored in ords
   ;; Each tuple contains the shape type and the number of ordinates
   :ords-info (m/not-indexed (m/stored m/int-field-mapping))
   ;; ords contains longitude latitude pairs (ordinates) of all the shapes
   :ords (m/not-indexed (m/stored m/int-field-mapping))})

(defmapping collection-mapping :collection
  "Defines the elasticsearch mapping for storing collections. These are the
  fields that will be stored in an Elasticsearch document. Note, fields can only
  be added to Elasticsearch, not removed or renamed."
  (merge {:deleted m/bool-field-mapping ; deleted=true is a tombstone
          :native-id m/string-field-mapping
          :native-id-lowercase m/string-field-mapping
          :user-id m/string-field-mapping

          ;; This comes from the metadata db column of the same name
          ;; and is by default equal to the Oracle system time at the
          ;; time the revision record is written
          :revision-date m/date-field-mapping
          :created-at m/date-field-mapping

          :permitted-group-ids m/string-field-mapping
          :concept-id   m/string-field-mapping
          :revision-id m/int-field-mapping

          ;; DEPRECATED integer type is no longer sufficient for this field
          :concept-seq-id m/int-field-mapping

          ;; This is used explicitly for sorting. The values take up less space in the fielddata cache.
          :concept-seq-id-long m/unsigned-long-field-mapping
          :entry-id           m/string-field-mapping
          :entry-id-lowercase m/string-field-mapping
          :doi           m/string-field-mapping
          :doi-stored    m/string-field-mapping
          :doi-lowercase m/string-field-mapping
          :entry-title           m/string-field-mapping
          :entry-title-lowercase m/string-field-mapping
          :provider-id           m/string-field-mapping
          :provider-id-lowercase m/string-field-mapping
          :short-name            m/string-field-mapping
          :short-name-lowercase  m/string-field-mapping
          :version-id            m/string-field-mapping
          :version-id-lowercase  m/string-field-mapping
          :parsed-version-id-lowercase m/string-field-mapping

          ;; Stored to allow retrieval for implementing granule acls
          :access-value                   m/float-field-mapping
          :processing-level-id            m/string-field-mapping
          :processing-level-id-lowercase  m/string-field-mapping
          :processing-level-id-lowercase-humanized m/string-field-mapping
          :processing-level-id-humanized prioritized-humanizer-mapping
          :latency                        m/string-field-mapping
          :latency-lowercase              m/string-field-mapping
          :collection-data-type           m/string-field-mapping
          :collection-data-type-lowercase m/string-field-mapping

          ;; Temporal date range
          :start-date                     m/date-field-mapping
          :end-date                       m/date-field-mapping
          :ongoing                        m/date-field-mapping
          :temporal-ranges                m/date-field-mapping

          ;; Temporal range of min and max granule values or the same as collection start and end date
          ;; if the collection has not granules.
          :granule-start-date             m/date-field-mapping
          :granule-end-date               m/date-field-mapping
          :granule-start-date-stored      m/date-field-mapping
          :granule-end-date-stored        m/date-field-mapping

          :has-granules m/bool-field-mapping
          :has-granules-or-cwic m/bool-field-mapping
          :has-granules-or-opensearch m/bool-field-mapping
          :has-variables m/bool-field-mapping
          :has-formats m/bool-field-mapping
          :has-transforms m/bool-field-mapping
          :has-spatial-subsetting m/bool-field-mapping
          :has-temporal-subsetting m/bool-field-mapping
          :has-opendap-url m/bool-field-mapping
          :cloud-hosted m/bool-field-mapping
          :standard-product m/bool-field-mapping

          :platform-sn                    m/string-field-mapping
          :platform-sn-lowercase          m/string-field-mapping
          :platform-sn-lowercase-humanized m/string-field-mapping
          :platform-sn-humanized          prioritized-humanizer-mapping
          :instrument-sn                  m/string-field-mapping
          :instrument-sn-lowercase        m/string-field-mapping
          :instrument-sn-lowercase-humanized m/string-field-mapping
          :instrument-sn-humanized        prioritized-humanizer-mapping
          :sensor-sn                      m/string-field-mapping
          :sensor-sn-lowercase            m/string-field-mapping
          :project-sn                     m/string-field-mapping
          :project-sn-lowercase           m/string-field-mapping
          :project-sn-lowercase-humanized m/string-field-mapping
          :project-sn-humanized           prioritized-humanizer-mapping
          :archive-center                 m/string-field-mapping
          :archive-center-lowercase       m/string-field-mapping
          :data-center                    m/string-field-mapping
          :data-center-lowercase          m/string-field-mapping
          :spatial-keyword                m/string-field-mapping
          :spatial-keyword-lowercase      m/string-field-mapping
          :two-d-coord-name               m/string-field-mapping
          :two-d-coord-name-lowercase     m/string-field-mapping
          :attributes                     attributes-field-mapping
          :downloadable                   m/bool-field-mapping
          :authors                        (m/doc-values m/string-field-mapping)
          :authors-lowercase              (m/doc-values m/string-field-mapping)

          :consortiums                    m/string-field-mapping
          :consortiums-lowercase          m/string-field-mapping

          ;; Mappings for nested fields used for searching and
          ;; hierarchical facets
          :science-keywords science-keywords-field-mapping
          :science-keywords-humanized science-keywords-field-mapping

          :granule-data-format                  m/string-field-mapping
          :granule-data-format-lowercase        m/string-field-mapping
          :granule-data-format-humanized        prioritized-humanizer-mapping

          ;;DEPRECATED see :platforms2
          :platforms platform-hierarchical-mapping
          :platforms2 platform2-hierarchical-mapping
          :platforms2-humanized platform2-hierarchical-mapping

          :instruments instrument-hierarchical-mapping
          :archive-centers data-center-hierarchical-mapping
          :location-keywords location-keywords-hierarchical-mapping
          ;; Contains all four types of data centers combined - archive,
          ;; centers, distribution centers, processing centers, and
          ;; originating centers.
          :data-centers data-center-hierarchical-mapping

          ;; nested mapping containing all the temporal ranges within a collection.
          :temporals temporal-mapping

          ;; nested mapping for limit_to_granules case.
          ;; it either contains [{:start-date granule-start-date-stored :end-date granule-end-date-stored}]
          ;; or when there're no granules, contains all the temporal ranges within the collection
          :limit-to-granules-temporals temporal-mapping

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
          :browsable m/bool-field-mapping
          :atom-links (m/not-indexed m/string-field-mapping)
          :summary m/english-string-field-mapping
          :metadata-format (m/not-indexed m/string-field-mapping)
          :update-time (m/not-indexed m/string-field-mapping)
          :index-time (m/not-indexed m/string-field-mapping)
          :associated-difs m/string-field-mapping
          :associated-difs-lowercase m/string-field-mapping
          :coordinate-system (m/not-indexed m/string-field-mapping)

          ;; mappings added for opendata
          :insert-time (m/not-indexed m/string-field-mapping)
          ;; This field contains multiple values obtained by
          ;; concatenating the category, topic, and term from
          ;; each science keyword. It represents the 'keywords'
          ;; field in the opendata format.
          :science-keywords-flat m/string-field-mapping
          :related-urls m/string-field-mapping
          :publication-references m/string-field-mapping
          :collection-citations m/string-field-mapping
          :contact-email m/string-field-mapping
          :personnel m/string-field-mapping

          ;; analyzed field for keyword searches
          :keyword2 m/text-field-keyword-mapping
          :long-name-lowercase m/string-field-mapping
          :project-ln-lowercase m/string-field-mapping
          :platform-ln-lowercase m/string-field-mapping
          :instrument-ln-lowercase m/string-field-mapping
          :sensor-ln-lowercase m/string-field-mapping
          :temporal-keyword-lowercase m/string-field-mapping

          ;; orbit parameters
          :swath-width m/double-field-mapping
          :period m/double-field-mapping
          :inclination-angle m/double-field-mapping
          :number-of-orbits m/double-field-mapping
          :start-circular-latitude m/double-field-mapping

          ;; additional humanized facet fields
          :organization-lowercase-humanized m/string-field-mapping
          :organization-humanized prioritized-humanizer-mapping

          ;; associated tags
          :tags tag-associations-mapping
          ;; associated tags stored as EDN gzipped and base64 encoded for retrieving purpose
          :tags-gzip-b64 m/binary-field-mapping

          ;; associated variables
          :variable-names m/string-field-mapping
          :variable-names-lowercase m/string-field-mapping
          :variable-concept-ids (m/doc-values m/string-field-mapping)
          :variable-native-ids (m/doc-values m/string-field-mapping)
          :variable-native-ids-lowercase (m/doc-values m/string-field-mapping)
          :measurements m/string-field-mapping
          :measurements-lowercase m/string-field-mapping
          :variables variables-mapping

          ;; associated services
          :service-names (m/doc-values m/string-field-mapping)
          :service-names-lowercase (m/doc-values m/string-field-mapping)
          :service-types-lowercase (m/doc-values m/string-field-mapping)
          :service-concept-ids (m/doc-values m/string-field-mapping)

          ;; associated tools
          :tool-names (m/doc-values m/string-field-mapping)
          :tool-names-lowercase (m/doc-values m/string-field-mapping)
          :tool-types-lowercase (m/doc-values m/string-field-mapping)
          :tool-concept-ids (m/doc-values m/string-field-mapping)

          ;; service features stored as EDN gzipped and base64 encoded for retrieving purpose
          :service-features-gzip-b64 m/binary-field-mapping

          ;; associations with the collection stored as EDN gzipped and base64 encoded for retrieving purpose
          :associations-gzip-b64 m/binary-field-mapping

          ;; Relevancy score from community usage metrics
          :usage-relevancy-score m/int-field-mapping
          :horizontal-data-resolutions float-prioritized-mapping

          ;; Direct Distribution Information
          :s3-bucket-and-object-prefix-names m/string-field-mapping}
         spatial-coverage-fields))

(defmapping deleted-granule-mapping :deleted-granule
  "Defines the elasticsearch mapping for storing granules. These are the
  fields that will be stored in an Elasticsearch document."
  {:concept-id (m/doc-values m/string-field-mapping)
   :revision-date (m/doc-values m/date-field-mapping)
   :provider-id (m/doc-values m/string-field-mapping)
   :granule-ur (m/doc-values m/string-field-mapping)
   :parent-collection-id (m/doc-values m/string-field-mapping)})

(defmapping granule-mapping :granule
  "Defines the elasticsearch mapping for storing collections. These are the
  fields that will be stored in an Elasticsearch document."
  (merge
   {:concept-id m/string-field-mapping
    :revision-id m/int-field-mapping

    :native-id (m/doc-values m/string-field-mapping)
    :native-id-lowercase (m/doc-values m/string-field-mapping)
    :native-id-stored (m/doc-values m/string-field-mapping)

    ;; DEPRECATED integer type is no longer sufficient for this field
    :concept-seq-id m/int-field-mapping
    :concept-seq-id-doc-values (m/doc-values m/int-field-mapping)

    ;; This is used explicitly for sorting. The values take up less space in the fielddata cache.
    :concept-seq-id-long m/unsigned-long-field-mapping
    :concept-seq-id-long-doc-values (m/doc-values m/unsigned-long-field-mapping)

    :collection-concept-id m/string-field-mapping
    :collection-concept-id-doc-values (m/doc-values m/string-field-mapping)

    ;; DEPRECATED integer type is no longer sufficient for this field
    :collection-concept-seq-id m/int-field-mapping
    :collection-concept-seq-id-doc-values (m/doc-values m/int-field-mapping)

    ;; Used for aggregations. It takes up less space in the field data cache.
    :collection-concept-seq-id-long m/unsigned-long-field-mapping
    :collection-concept-seq-id-long-doc-values (m/doc-values m/unsigned-long-field-mapping)

    ;; fields added for atom
    :entry-title (m/not-indexed m/string-field-mapping)
    :metadata-format (m/not-indexed m/string-field-mapping)
    :update-time (m/not-indexed m/string-field-mapping)
    :coordinate-system (m/not-indexed m/string-field-mapping)

    ;; Collection fields added strictly for sorting granule results
    :entry-title-lowercase m/string-field-mapping
    :entry-title-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :short-name-lowercase m/string-field-mapping
    :short-name-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :version-id-lowercase m/string-field-mapping
    :version-id-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :provider-id m/string-field-mapping
    :provider-id-doc-values (m/doc-values m/string-field-mapping)

    :provider-id-lowercase m/string-field-mapping
    :provider-id-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :granule-ur m/string-field-mapping
    :granule-ur-lowercase (m/doc-values m/string-field-mapping)

    :producer-gran-id m/string-field-mapping
    :producer-gran-id-lowercase (m/doc-values m/string-field-mapping)

    :day-night m/string-field-mapping
    :day-night-doc-values (m/doc-values m/string-field-mapping)

    :day-night-lowercase m/string-field-mapping

    ;; Access value is stored to allow us to enforce acls after retrieving results
    ;; for certain types of queries.
    :access-value m/float-field-mapping
    :access-value-doc-values (m/doc-values m/float-field-mapping)

    ;; We need to sort by a combination of producer granule and granule ur
    ;; It should use producer granule id if present otherwise the granule ur is used
    ;; The producer granule id will be put in this field if present otherwise it
    ;; will default to granule-ur. This avoids the solution Catalog REST uses which is
    ;; to use a sort script which is (most likely) much slower.
    :readable-granule-name-sort (m/doc-values m/string-field-mapping)

    :platform-sn  m/string-field-mapping
    :platform-sn-lowercase m/string-field-mapping
    :platform-sn-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :instrument-sn m/string-field-mapping
    :instrument-sn-lowercase m/string-field-mapping
    :instrument-sn-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :sensor-sn  m/string-field-mapping
    :sensor-sn-lowercase m/string-field-mapping
    :sensor-sn-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :feature-id m/string-field-mapping
    :feature-id-lowercase m/string-field-mapping

    :crid-id  m/string-field-mapping
    :crid-id-lowercase m/string-field-mapping

    :start-date m/date-field-mapping
    :start-date-doc-values (m/doc-values m/date-field-mapping)

    :end-date m/date-field-mapping
    :end-date-doc-values (m/doc-values m/date-field-mapping)

    ;; No longer indexing to :temporals due to performance issues, but cannot
    ;; delete from elastic index
    :temporals temporal-mapping
    :size m/double-field-mapping
    :size-doc-values (m/doc-values m/double-field-mapping)

    :cloud-cover m/float-field-mapping
    :cloud-cover-doc-values (m/doc-values m/float-field-mapping)

    :orbit-calculated-spatial-domains orbit-calculated-spatial-domain-mapping

    :project-refs m/string-field-mapping
    :project-refs-lowercase m/string-field-mapping
    :project-refs-lowercase-doc-values (m/doc-values m/string-field-mapping)

    :created-at (m/doc-values m/date-field-mapping)
    :production-date (m/doc-values m/date-field-mapping)
    :revision-date m/date-field-mapping
    :revision-date-doc-values (m/doc-values m/date-field-mapping)
    :revision-date-stored-doc-values (m/doc-values m/date-field-mapping)

    :downloadable m/bool-field-mapping
    :browsable m/bool-field-mapping
    :attributes attributes-field-mapping

    :two-d-coord-name m/string-field-mapping
    :two-d-coord-name-lowercase m/string-field-mapping
    :start-coordinate-1 m/double-field-mapping
    :end-coordinate-1 m/double-field-mapping
    :start-coordinate-2 m/double-field-mapping
    :end-coordinate-2 m/double-field-mapping

    :start-coordinate-1-doc-values (m/doc-values m/double-field-mapping)
    :end-coordinate-1-doc-values (m/doc-values m/double-field-mapping)
    :start-coordinate-2-doc-values (m/doc-values m/double-field-mapping)
    :end-coordinate-2-doc-values (m/doc-values m/double-field-mapping)

    :cycle (m/doc-values m/int-field-mapping)
    :passes track-pass-mapping

    ;; Used for orbit search
    :orbit-asc-crossing-lon m/double-field-mapping
    :orbit-asc-crossing-lon-doc-values (m/doc-values m/double-field-mapping)
    :orbit-start-clat m/double-field-mapping
    :orbit-start-clat-doc-values (m/doc-values m/double-field-mapping)
    :orbit-end-clat m/double-field-mapping
    :orbit-end-clat-doc-values (m/doc-values m/double-field-mapping)
    :start-lat m/double-field-mapping
    :start-direction m/string-field-mapping
    :end-lat m/double-field-mapping
    :end-direction m/string-field-mapping

    ;; atom-links is a json string that contains the atom-links, which is a list of
    ;; maps of atom link attributes. We tried to use nested document to save atom-links
    ;; as a structure in elasticsearch, but can't find a way to retrieve it out.
    ;; So we are saving the links in json string, then parse it out when we need it.
    :atom-links (m/not-indexed m/string-field-mapping)

    ;; :orbit-calculated-spatial-domains-json is json string
    ;; stored for retrieval similar to :atom-links above
    :orbit-calculated-spatial-domains-json (m/not-indexed m/string-field-mapping)}

   spatial-coverage-fields))

(defmapping tag-mapping :tag
  "Defines the elasticsearch mapping for storing tags. These are the fields
  that will be stored in an Elasticsearch document."
  {:concept-id m/string-field-mapping
   :tag-key-lowercase (m/doc-values m/string-field-mapping)
   :description (m/not-indexed m/string-field-mapping)
   :originator-id-lowercase m/string-field-mapping})

(defmapping autocomplete-mapping :suggestion
  "Defines the elasticsearch mapping for storing autocomplete suggestions.
   These are the fields that will be stored in an Elasticsearch document."
  {:type m/string-field-mapping
   :fields (m/not-indexed m/string-field-mapping)
   :value m/search-as-you-type-field-mapping
   :contains-public-collections (m/doc-values m/bool-field-mapping)
   :permitted-group-ids (m/doc-values m/string-field-mapping)
   :modified (m/doc-values m/date-field-mapping)})

(defmapping variable-mapping :variable
  "Defines the elasticsearch mapping for storing variables. These are the
  fields that will be stored in an Elasticsearch document."
  {:concept-id (m/doc-values m/string-field-mapping)
   :revision-id (m/doc-values m/int-field-mapping)

   ;; DEPRECATED integer type is no longer sufficient for this field
   :concept-seq-id (m/doc-values m/int-field-mapping)

   ;; This is used explicitly for sorting. The values take up less space in the fielddata cache.
   :concept-seq-id-long (m/doc-values m/unsigned-long-field-mapping)
   :native-id (m/doc-values m/string-field-mapping)
   :native-id-lowercase (m/doc-values m/string-field-mapping)
   :provider-id (m/doc-values m/string-field-mapping)
   :provider-id-lowercase (m/doc-values m/string-field-mapping)
   :variable-name (m/doc-values m/string-field-mapping)
   :variable-name-lowercase (m/doc-values m/string-field-mapping)
   :measurement (m/doc-values m/string-field-mapping)
   :measurement-lowercase (m/doc-values m/string-field-mapping)
   :keyword m/text-field-mapping
   :deleted (m/doc-values m/bool-field-mapping)
   :user-id (m/doc-values m/string-field-mapping)
   :revision-date (m/doc-values m/date-field-mapping)
   :metadata-format (m/doc-values m/string-field-mapping)
   :measurement-identifiers measurement-identifiers-mapping
   ;; associations with the variable stored as EDN gzipped and base64 encoded for retrieving purpose
   :associations-gzip-b64 m/binary-field-mapping})

(defmapping service-mapping :service
  "Defines the elasticsearch mapping for storing services. These are the
  fields that will be stored in an Elasticsearch document."
  {:concept-id (m/doc-values m/string-field-mapping)
   :revision-id (m/doc-values m/int-field-mapping)
   :native-id (m/doc-values m/string-field-mapping)
   :native-id-lowercase (m/doc-values m/string-field-mapping)
   :provider-id (m/doc-values m/string-field-mapping)
   :provider-id-lowercase (m/doc-values m/string-field-mapping)
   :service-name (m/doc-values m/string-field-mapping)
   :service-name-lowercase (m/doc-values m/string-field-mapping)
   :long-name (m/doc-values m/string-field-mapping)
   :long-name-lowercase (m/doc-values m/string-field-mapping)
   :service-type-lowercase (m/doc-values m/string-field-mapping)
   :keyword m/text-field-mapping
   :deleted (m/doc-values m/bool-field-mapping)
   :user-id (m/doc-values m/string-field-mapping)
   :revision-date (m/doc-values m/date-field-mapping)
   :metadata-format (m/doc-values m/string-field-mapping)
   ;; associations with the service stored as EDN gzipped and base64 encoded for retrieving purpose 
   :associations-gzip-b64 m/binary-field-mapping})

(defmapping tool-mapping :tool
  "Defines the elasticsearch mapping for storing tools. These are the
  fields that will be stored in an Elasticsearch document."
  {:concept-id (m/doc-values m/string-field-mapping)
   :revision-id (m/doc-values m/int-field-mapping)
   :native-id (m/doc-values m/string-field-mapping)
   :native-id-lowercase (m/doc-values m/string-field-mapping)
   :provider-id (m/doc-values m/string-field-mapping)
   :provider-id-lowercase (m/doc-values m/string-field-mapping)
   :tool-name (m/doc-values m/string-field-mapping)
   :tool-name-lowercase (m/doc-values m/string-field-mapping)
   :long-name (m/doc-values m/string-field-mapping)
   :long-name-lowercase (m/doc-values m/string-field-mapping)
   :tool-type-lowercase (m/doc-values m/string-field-mapping)
   :keyword m/text-field-mapping
   :deleted (m/doc-values m/bool-field-mapping)
   :user-id (m/doc-values m/string-field-mapping)
   :revision-date (m/doc-values m/date-field-mapping)
   :metadata-format (m/doc-values m/string-field-mapping)
   ;; associations with the tool stored as EDN gzipped and base64 encoded for retrieving purpose
   :associations-gzip-b64 m/binary-field-mapping})

(defmapping subscription-mapping :subscription
  "Defines the elasticsearch mapping for storing subscriptions. These are the
  fields that will be stored in an Elasticsearch document."
  {:concept-id (m/doc-values m/string-field-mapping)
   :revision-id (m/doc-values m/int-field-mapping)
   :native-id (m/doc-values m/string-field-mapping)
   :native-id-lowercase (m/doc-values m/string-field-mapping)
   :provider-id (m/doc-values m/string-field-mapping)
   :provider-id-lowercase (m/doc-values m/string-field-mapping)
   :subscription-name (m/doc-values m/string-field-mapping)
   :subscription-name-lowercase (m/doc-values m/string-field-mapping)
   :collection-concept-id (m/doc-values m/string-field-mapping)
   :collection-concept-id-lowercase (m/doc-values m/string-field-mapping)
   :subscriber-id (m/doc-values m/string-field-mapping)
   :subscriber-id-lowercase (m/doc-values m/string-field-mapping)
   :subscription-type (m/doc-values m/string-field-mapping)
   :subscription-type-lowercase (m/doc-values m/string-field-mapping)
   :deleted (m/doc-values m/bool-field-mapping)
   :user-id (m/doc-values m/string-field-mapping)
   :creation-date (m/doc-values m/date-field-mapping)
   :revision-date (m/doc-values m/date-field-mapping)
   :metadata-format (m/doc-values m/string-field-mapping)})

;; there is no defmapping for generic documents, instead see
;; cmr.ingest.api.generic-documents/generic_documents.clj

(def granule-settings-for-individual-indexes
  {:index {:number_of_shards (elastic-granule-index-num-shards),
           :number_of_replicas 1,
           :max_result_window MAX_RESULT_WINDOW,
           :refresh_interval "1s"}})

(def granule-settings-for-small-collections-index
  {:index {:number_of_shards (elastic-small-collections-index-num-shards),
           :number_of_replicas 1,
           :max_result_window MAX_RESULT_WINDOW,
           :refresh_interval "1s"}})

(def index-set-id
  "The identifier of the one and only index set"
  1)

(defn index-set
  "Returns the index-set configuration for a brand new index. Takes a list of the extra
   granule indexes that should exist in addition to small_collections. This function
   produces a map containing a list of indexes which contain a settings and a mapping
   map like this:
   {:index-set {
     <doc-type> {
       :indexes [{:name '' :settings {<shards and replicas>}}]
       :mapping {:properties{:example {:type 'keyword'}}}}}}
   Note: Indexes normally have two items, the all revisions index and the normal index
   Note: Most mappings include a litaral case version and a lowercase version
   "
  [extra-granule-indexes]
  (let [set-of-indexes {:name "cmr-base-index-set"
               :id index-set-id
               :create-reason "indexer app requires this index set"
               :collection {:indexes
                            [;; This index contains the latest revision of each collection and
                             ;; is used for normal searches.
                             {:name "collections-v2"
                              :settings collection-setting-v2}
                             ;; This index contains all the revisions (including tombstones) and
                             ;; is used for all-revisions searches.
                             {:name "all-collection-revisions"
                              :settings collection-setting-v1}]
                            :mapping collection-mapping}
               :deleted-granule {:indexes
                                 [{:name "deleted_granules"
                                   :settings deleted-granule-setting}]
                                 :mapping deleted-granule-mapping}
               :granule {:indexes
                         (cons {:name "small_collections"
                                :settings granule-settings-for-small-collections-index}
                               (for [idx extra-granule-indexes]
                                 {:name idx
                                  :settings granule-settings-for-individual-indexes}))
                         ;; This specifies the settings for new granule indexes that contain data for a single collection
                         ;; This allows the index set application to know what settings to use when creating
                         ;; a new granule index.
                         :individual-index-settings granule-settings-for-individual-indexes
                         :mapping granule-mapping}
               :tag {:indexes
                     [{:name "tags"
                       :settings tag-setting}]
                     :mapping tag-mapping}
               :variable {:indexes
                          [{:name "variables"
                            :settings variable-setting}
                           ;; This index contains all the revisions (including tombstones) and
                           ;; is used for all-revisions searches.
                           {:name "all-variable-revisions"
                            :settings variable-setting}]
                          :mapping variable-mapping}
               :autocomplete {:indexes
                              [{:name "autocomplete"
                                :settings autocomplete-settings}]
                              :mapping autocomplete-mapping}
               :service {:indexes
                         [{:name "services"
                           :settings service-setting}
                          ;; This index contains all the revisions (including tombstones) and
                          ;; is used for all-revisions searches.
                          {:name "all-service-revisions"
                           :settings service-setting}]
                         :mapping service-mapping}
               :tool {:indexes
                      [{:name "tools"
                        :settings tool-setting}
                       ;; This index contains all the revisions (including tombstones) and
                       ;; is used for all-revisions searches.
                       {:name "all-tool-revisions"
                        :settings tool-setting}]
                      :mapping tool-mapping}
               :subscription {:indexes
                              [{:name "subscriptions"
                                :settings subscription-setting}
                                ;; This index contains all the revisions (including tombstones) and
                                ;; is used for all-revisions searches.
                               {:name "all-subscription-revisions"
                                :settings subscription-setting}]
                              :mapping subscription-mapping}}]

               ;; merge into the set of indexes all the configured generic documents
               {:index-set (reduce (fn [data addition] (merge data addition))
                                   set-of-indexes
                                   (index-set-gen/generic-mappings-generator))}))

(defn index-set->extra-granule-indexes
  "Takes an index set and returns the extra granule indexes that are configured"
  [index-set]
  (->> (get-in index-set [:index-set :granule :indexes])
       (map :name)
       (remove #(= % "small_collections"))))

(defn fetch-concept-type-index-names
  "Fetch a map containing index names for each concept type from index-set app along with the list
   of rebalancing collections"
  ([context]
   (let [index-set-id (get-in (index-set context) [:index-set :id])]
     (fetch-concept-type-index-names context index-set-id)))
  ([context index-set-id]
   (let [fetched-index-set (index-set-es/get-index-set context index-set-id)]
     {:index-names (get-in fetched-index-set [:index-set :concepts])
      :rebalancing-collections (get-in fetched-index-set
                                       [:index-set :granule :rebalancing-collections])})))

(defn get-concept-mapping-types-for-generics
  "This function sets up the concept mapping types for generics. Any generic that doesn't have a
   map is ommitted."
  [concept-type fetched-index-set]
  (let [index-type (keyword (format "generic-%s" (name concept-type)))
        mapping (get-in fetched-index-set [:index-set index-type :mapping])]
    (when mapping
      (let [mapping-type (-> mapping
                             keys
                             first
                             name)]
        {index-type (str mapping-type)}))))

(defn fetch-concept-mapping-types
  "Fetch mapping types for each concept type from index-set app, returns a map of
   concept types which define what the top level field is in each mapping description.
   Normally this is 'properties'."
  ([context]
   (let [index-set-id (get-in (index-set context) [:index-set :id])]
     (fetch-concept-mapping-types context index-set-id)))
  ([context index-set-id]
   (let [fetched-index-set (index-set-es/get-index-set context index-set-id)
         get-concept-mapping-fn (fn [concept-type]
                                  (-> (get-in fetched-index-set [:index-set concept-type :mapping])
                                      keys
                                      first
                                      name))]
     (merge
      {:collection (get-concept-mapping-fn :collection)
       :granule (get-concept-mapping-fn :granule)
       :tag (get-concept-mapping-fn :tag)
       :variable (get-concept-mapping-fn :variable)
       :service (get-concept-mapping-fn :service)
       :tool (get-concept-mapping-fn :tool)
       :subscription (get-concept-mapping-fn :subscription)}
      (into {}
            (map #(get-concept-mapping-types-for-generics % fetched-index-set)
                 (cs/get-generic-concept-types-array)))))))

(defn fetch-rebalancing-collection-info
  "Fetch rebalancing collections, their targets, and status."
  ([context]
   (let [index-set-id (get-in (index-set context) [:index-set :id])]
     (fetch-rebalancing-collection-info context index-set-id)))
  ([context index-set-id]
   (let [fetched-index-set (get-in (index-set-es/get-index-set context index-set-id) [:index-set :granule])]
     (select-keys fetched-index-set [:rebalancing-collections :rebalancing-status :rebalancing-targets]))))

(def index-set-cache-key
  "The name of the cache used for caching index set related data."
  :indexer-index-set-cache)

(defn get-concept-type-index-names
  "Fetch index names associated with concepts."
  [context]
  (let [cache (cache/context->cache context index-set-cache-key)]
    (cache/get-value cache :concept-indices (partial fetch-concept-type-index-names context))))

(defn get-concept-mapping-types
  "Fetch mapping types associated with concepts. Should be a map of index types
   with the name of the top level field in the mapping description."
  [context]
  (let [cache (cache/context->cache context index-set-cache-key)]
    (cache/get-value cache :concept-mapping-types (partial fetch-concept-mapping-types context))))

(defn get-granule-index-names-for-collection
  "Return the granule index names for the input collection concept id. Optionally a
   target-index-key can be specified which indicates that a specific index should be returned"
  ([context coll-concept-id]
   (get-granule-index-names-for-collection context coll-concept-id nil))
  ([context coll-concept-id target-index-key]
   (let [{:keys [index-names rebalancing-collections]} (get-concept-type-index-names context)
         indexes (:granule index-names)
         small-collections-index-name (get indexes :small_collections)]

     (cond
       target-index-key
       [(get indexes target-index-key)]

       ;; The collection is currently rebalancing so it will have granules in both small Collections
       ;; and the separate index
       (some #{coll-concept-id} rebalancing-collections)
       [(get indexes (keyword coll-concept-id)) small-collections-index-name]

       :else
       ;; The collection is not rebalancing so it's either in a separate index or small Collections
       [(get indexes (keyword coll-concept-id) small-collections-index-name)]))))

(defn resolve-generic-concept-type
  "If the concept type is generic, figure out from the concept what the actual document type is"
  [concept-type]
  (if (cs/generic-concept? concept-type)
    (keyword (format "generic-%s" (name concept-type)))
    concept-type))

(defn get-concept-index-names
  "Return the concept index names for the given concept id.
   Valid options:
   * target-index-key - Specifies a key into the index names map to choose an index to get to override
     the default.
   * all-revisions-index? - true indicates we should target the all collection revisions index.
   Example:
   {:index-names {
      :service {
        :services '1_services', :all-service-revisions '1_all_service_revisions'}}
   "
  ([context concept-id revision-id options]
   (let [concept-type (cs/concept-id->type concept-id)
         concept (when (= :granule concept-type)
                   (meta-db/get-concept context concept-id revision-id))]
     (get-concept-index-names context concept-id revision-id options concept)))
  ([context concept-id revision-id {:keys [target-index-key all-revisions-index?]} concept]
   (let [concept-type (cs/concept-id->type concept-id)
         index-concept-type (resolve-generic-concept-type concept-type)
         indexes (get-in (get-concept-type-index-names context) [:index-names index-concept-type])]
     (case concept-type
       :collection
       (cond
         target-index-key [(get indexes target-index-key)]
         all-revisions-index? [(get indexes :all-collection-revisions)]
         ;; Else index to all collection indexes except for the all-collection-revisions index.
         :else (keep (fn [[k v]]
                       (when-not (= :all-collection-revisions (keyword k))
                         v))
                     indexes))

       :tag
       [(get indexes (or target-index-key :tags))]

       :variable
       (if all-revisions-index?
         [(get indexes :all-variable-revisions)]
         [(get indexes (or target-index-key :variables))])

       :service
       (if all-revisions-index?
         [(get indexes :all-service-revisions)]
         [(get indexes (or target-index-key :services))])

       :tool
       (if all-revisions-index?
         [(get indexes :all-tool-revisions)]
         [(get indexes (or target-index-key :tools))])

       :subscription
       (if all-revisions-index?
         [(get indexes :all-subscription-revisions)]
         [(get indexes (or target-index-key :subscriptions))])

       :granule
       (let [coll-concept-id (:parent-collection-id (:extra-fields concept))]
         (get-granule-index-names-for-collection context coll-concept-id target-index-key))

       ;; Default
       (if (some? (concept-type (common-generic/latest-approved-documents)))
         ;; Generics are a bunch of document types, find out which one to work with
         ;; and return the index name for those
         (if all-revisions-index?
           [(get indexes (keyword (format "all-generic-%s-revisions" (name concept-type))))]
           [(get indexes (keyword (format "generic-%s" (name concept-type))))]))))))

(defn get-granule-index-names-for-provider
  "Return the granule index names for the input provider id"
  [context provider-id]
  (let [indexes (get-in (get-concept-type-index-names context) [:index-names :granule])
        filter-fn (fn [[k v]]
                    (or
                      (.endsWith (name k) (str "_" provider-id))
                      (= :small_collections k)))]
    (map second (filter filter-fn indexes))))
