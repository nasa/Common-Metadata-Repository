(ns cmr.indexer.data.concepts.collection
  "Contains functions to parse and convert collection concept"
  (:require
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.acl.acl-fetcher :as acl-fetcher]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.elastic-utils.index-util :as index-util]
   [cmr.indexer.config :as indexer-config]
   [cmr.indexer.data.collection-granule-aggregation-cache :as cgac]
   [cmr.indexer.data.concepts.attribute :as attrib]
   [cmr.indexer.data.concepts.collection.collection-util :as collection-util]
   [cmr.indexer.data.concepts.collection.community-usage-metrics :as metrics]
   [cmr.indexer.data.concepts.collection.data-center :as data-center]
   [cmr.indexer.data.concepts.collection.distributed-format-util :as dist-util]
   [cmr.indexer.data.concepts.collection.humanizer :as humanizer]
   [cmr.indexer.data.concepts.collection.instrument :as instrument]
   [cmr.indexer.data.concepts.collection.keyword :as k]
   [cmr.indexer.data.concepts.collection.location-keyword :as clk]
   [cmr.indexer.data.concepts.collection.opendata :as opendata]
   [cmr.indexer.data.concepts.collection.platform :as platform]
   [cmr.indexer.data.concepts.collection.science-keyword :as sk]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.concepts.service :as service]
   [cmr.indexer.data.concepts.spatial :as spatial]
   [cmr.indexer.data.concepts.subscription :as subscription]
   [cmr.indexer.data.concepts.tag :as tag]
   [cmr.indexer.data.concepts.tool :as tool]
   [cmr.indexer.data.concepts.variable :as variable]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.umm-spec.acl-matchers :as umm-matchers]
   [cmr.umm-spec.date-util :as date-util]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.models.umm-collection-models :as umm-collection]
   [cmr.umm-spec.opendap-util :as opendap-util]
   [cmr.umm-spec.related-url :as ru]
   [cmr.umm-spec.time :as spec-time]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm-spec.util :as su]
   [cmr.umm.collection.entry-id :as eid]
   [cmr.umm.umm-collection :as umm-c]))

(defn spatial->elastic
  [collection]
  (when-let [coord-sys (spatial/get-collection-coordinate-system collection)]
    (cond
      (or (= coord-sys :geodetic) (= coord-sys :cartesian))
      (spatial/collection-spatial->elastic-docs coord-sys collection)

      :else
      (errors/internal-error! (str "Unknown spatial representation [" coord-sys "]")))))

(defn- apply-function-to-all-values-in-map
  "Applies function f to all the values in map m."
  [m f]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} m))

(defn- determine-ongoing-date
  "Determines ongoing date using collection's end-date. Ongoing is a date-time field in elastic,
   and if a collection is determined as ongoing, it uses January 1 3000 for sorting purposes.
   If its not an ongoing collection, end-date is used.  This is to keep the end-date
   sorting for collections that are not ongoing."
  [end-date]
  (if end-date
    (if (t/after?
         end-date
         (t/minus (tk/now) (t/days (indexer-config/ongoing-days))))
      (index-util/date->elastic (t/date-time 3000 1 1 0 0 0 0))
      (index-util/date->elastic end-date))
    (index-util/date->elastic (t/date-time 3000 1 1 0 0 0 0))))

(defn- collection-temporal-elastic
  "Returns a map of collection temporal fields for indexing in Elasticsearch."
  [context concept-id collection]
  (let [start-date (spec-time/collection-start-date collection)
        end-date (spec-time/normalized-end-date collection)
        {:keys [granule-start-date granule-end-date]}
        (cgac/get-coll-gran-aggregates context concept-id)
        last-3-days (t/interval (t/minus (tk/now) (t/days 3)) (tk/now))
        granule-end-date (when-not (and granule-end-date
                                        (t/within? last-3-days granule-end-date))
                           ;; If the granule end date is within the last 3 days we indicate that
                           ;; the collection has no end date. This allows NRT collections to be
                           ;; found even if the collection has been reindexed recently.
                           granule-end-date)
        coll-start (index-util/date->elastic start-date)
        coll-end (index-util/date->elastic end-date)]
    (merge {:start-date coll-start
            :end-date coll-end
            :ongoing (determine-ongoing-date end-date)}
           (or (when granule-start-date
                 {:granule-start-date-stored (index-util/date->elastic granule-start-date)
                  :granule-end-date-stored (index-util/date->elastic granule-end-date)})
               ;; Use the collection start and end date if there are no granule start and end dates.
               {:granule-start-date-stored coll-start
                :granule-end-date-stored coll-end}))))

(defn- assoc-nil-if
  "Set value to nil if the predicate is true
  Uses assoc."
  [collection key predicate]
  (if predicate
    (assoc collection key nil)
    collection))

(defn- assoc-in-nil-if
  "Set value to nil if the predicate is true.
  Uses assoc-in."
  [collection keys predicate]
  (if predicate
    (assoc-in collection keys nil)
    collection))

(defn- sanitize-processing-level-ids
  "Sanitize Processing Level Ids if and only if the values are default"
  [collection]
  (assoc-in-nil-if collection
                   [:ProcessingLevel :Id]
                   (= (get-in collection [:ProcessingLevel :Id]) su/not-provided)))

(defn- remove-index-irrelevant-defaults
  "Remove default values irrelevant to indexing to avoid them being indexed"
  [collection]
  (-> collection
      sanitize-processing-level-ids
      (assoc-nil-if :Platforms (= (:Platforms collection) su/not-provided-platforms))
      (assoc-nil-if :RelatedUrls (= (:RelatedUrls collection) [su/not-provided-related-url]))
      (assoc-nil-if :ScienceKeywords (= (:ScienceKeywords collection) su/not-provided-science-keywords))
      (assoc-nil-if :DataCenters (= (:DataCenters collection) [su/not-provided-data-center]))))

(defn- associations->gzip-base64-str
  "Returns the gziped base64 string for the given variable associations and service associations"
  [variable-associations service-associations]
  (when (or (seq variable-associations)
            (seq service-associations))
    (util/string->gzip-base64
     (pr-str
      (util/remove-map-keys empty?
                            {:variables (mapv :variable-concept-id variable-associations)
                             :services (mapv :service-concept-id service-associations)})))))

(defn- variable-service-associations->elastic-docs
  "Returns the elastic docs for variable and service assocations"
  [context variable-associations service-associations]
  (let [variable-docs (variable/variable-associations->elastic-doc context variable-associations)
        service-docs (service/service-associations->elastic-doc context service-associations)
        has-variables (or (:has-variables variable-docs) (:has-variables service-docs))]
    (merge variable-docs service-docs {:has-variables has-variables})))

(defn- get-granule-data-format
  "Returns the Format field from
   ArchiveAndDistributionInformation -> FileDistributionInformation. This also parses the format
   string for multiple values and filters out encoded information."
  [file-distribution-information]
  (->> file-distribution-information
       (map :Format)
       (mapcat dist-util/parse-distribution-formats)
       distinct
       (remove nil?)))

(defn- get-elastic-doc-for-full-collection
  "Get all the fields for a normal collection index operation."
  [context concept collection]
  (let [{:keys [concept-id revision-id provider-id user-id native-id
                created-at revision-date deleted format extra-fields
                tag-associations variable-associations service-associations]} concept
        collection (merge {:concept-id concept-id} (remove-index-irrelevant-defaults collection))
        {short-name :ShortName version-id :Version entry-title :EntryTitle
         collection-data-type :CollectionDataType summary :Abstract
         temporal-keywords :TemporalKeywords platforms :Platforms
         related-urls :RelatedUrls collection-temporal-extents :TemporalExtents
         publication-references :PublicationReferences
         collection-citations :CollectionCitations} collection
        parsed-version-id (collection-util/parse-version-id version-id)
        doi (get-in collection [:DOI :DOI])
        doi-lowercase (util/safe-lowercase doi)
        processing-level-id (get-in collection [:ProcessingLevel :Id])
        spatial-keywords (lk/location-keywords->spatial-keywords-for-indexing
                          (:LocationKeywords collection))
        access-value (get-in collection [:AccessConstraints :Value])
        collection-data-type (if (= "NEAR_REAL_TIME" collection-data-type)
                               ;; add in all the aliases for NEAR_REAL_TIME
                               (concat [collection-data-type] keyword-util/nrt-aliases)
                               collection-data-type)
        entry-id (eid/entry-id short-name version-id)
        opendata-related-urls (map opendata/related-url->opendata-related-url related-urls)
        opendata-references (keep opendata/publication-reference->opendata-reference
                                  publication-references)
        opendata-citations (keep opendata/collection-citation->opendata-citation collection-citations)
        personnel (opendata/opendata-email-contact collection)
        platforms (map util/map-keys->kebab-case platforms)
        kms-index (kf/get-kms-index context)
        platforms-nested (map #(platform/platform-short-name->elastic-doc kms-index %)
                              (map :short-name platforms))
        platform-short-names (->> (map :short-name platforms-nested)
                                  (map str/trim))
        platform-long-names (->> (distinct (keep :long-name (concat platforms platforms-nested)))
                                 (map str/trim))
        instruments (mapcat :instruments platforms)
        instruments (concat instruments (mapcat :composed-of instruments))
        instruments-nested (map #(instrument/instrument-short-name->elastic-doc kms-index %)
                                (keep :short-name instruments))
        instrument-short-names (->> instruments-nested
                                    (map :short-name)
                                    distinct
                                    (map str/trim))
        instrument-long-names (->> (distinct (keep :long-name
                                                   (concat instruments instruments-nested)))
                                   (map str/trim))
        sensors (mapcat :composed-of instruments)
        sensor-short-names (keep :short-name sensors)
        sensor-long-names (keep :long-name sensors)
        project-short-names (->> (map :ShortName (:Projects collection))
                                 (map str/trim))
        project-long-names (->> (keep :LongName (:Projects collection))
                                (map str/trim))
        ;; Pull author info from both creator and other citation details
        authors (->> (concat
                      (keep :Creator (:CollectionCitations collection))
                      (keep :OtherCitationDetails (:CollectionCitations collection)))
                     (map str/trim))
        two-d-coord-names (map :TilingIdentificationSystemName
                               (:TilingIdentificationSystems collection))
        meaningful-short-name-fn (fn [c]
                                   (when-let [short-name (:short-name c)]
                                     (when (not= su/not-provided short-name)
                                       short-name)))
        archive-centers (map #(data-center/data-center-short-name->elastic-doc kms-index %)
                             (map str/trim (data-center/extract-archive-center-names collection)))
        ;; get the normalized names back
        archive-center-names (keep meaningful-short-name-fn archive-centers)
        data-centers (map #(data-center/data-center-short-name->elastic-doc kms-index %)
                          (map str/trim (data-center/extract-data-center-names collection)))
        ;; returns a list of {:start-date xxx :end-date yyy}
        temporal-extents (->> collection-temporal-extents
                              ;; converts temporal-extents into a list of many
                              ;; {:BeginningDateTime xxx :EndingDateTime xxx}
                              (mapcat spec-time/temporal-ranges)
                              (map #(set/rename-keys % {:BeginningDateTime :start-date
                                                        :EndingDateTime :end-date}))
                              (map #(apply-function-to-all-values-in-map % index-util/date->elastic)))
        temporal-extents-ranges (->> collection-temporal-extents
                                     (map #(dissoc % :SingleDateTimes))
                                     (mapcat spec-time/temporal-ranges)
                                     (map #(set/rename-keys % {:BeginningDateTime :start-date
                                                               :EndingDateTime :end-date}))
                                     (map #(apply-function-to-all-values-in-map % index-util/date->elastic)))
        data-center-names (keep meaningful-short-name-fn data-centers)
        atom-links (map json/generate-string (ru/atom-links related-urls))
        ;; not empty is used below to get a real true/false value
        downloadable (not (empty? (ru/downloadable-urls related-urls)))
        browsable (not (empty? (ru/browse-urls related-urls)))
        update-time (date-util/data-update-date collection)
        update-time (index-util/date->elastic update-time)
        index-time (index-util/date->elastic (tk/now))
        insert-time (date-util/data-create-date collection)
        insert-time (index-util/date->elastic insert-time)
        coordinate-system (get-in collection [:SpatialExtent :HorizontalSpatialDomain
                                              :Geometry :CoordinateSystem])
        permitted-group-ids (collection-util/get-coll-permitted-group-ids context provider-id collection)
        {:keys [granule-start-date granule-end-date]}
        (cgac/get-coll-gran-aggregates context concept-id)
        last-3-days (t/interval (t/minus (tk/now) (t/days 3)) (tk/now))
        granule-end-date (when-not (and granule-end-date
                                        (t/within? last-3-days granule-end-date))
                                  ;; If the granule end date is within the last 3 days we indicate that
                                  ;; the collection has no end date. This allows NRT collections to be
                                  ;; found even if the collection has been reindexed recently.
                                  ;; otherwise, use granule-end-date
                                  granule-end-date)
        humanized-values (humanizer/collection-humanizers-elastic context collection)
        tags (map tag/tag-association->elastic-doc tag-associations)
        has-granules (some? (cgac/get-coll-gran-aggregates context concept-id))
        granule-data-format (get-granule-data-format
                             (get-in collection [:ArchiveAndDistributionInformation
                                                 :FileDistributionInformation]))]

    (merge {:concept-id concept-id
            :doi-stored doi
            :doi-lowercase doi-lowercase
            :revision-id revision-id
            :concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
            :native-id native-id
            :native-id-lowercase (str/lower-case native-id)
            :user-id user-id
            :permitted-group-ids permitted-group-ids
            ;; If there's an entry in the collection granule aggregates then the collection has granules.
            :has-granules has-granules
            :has-granules-or-cwic (or
                                   has-granules
                                   (some?
                                    (some #(= (common-config/cwic-tag) %)
                                          (map :tag-key-lowercase tags))))
            :granule-data-format granule-data-format
            :granule-data-format-lowercase (map str/lower-case granule-data-format)
            :entry-id entry-id
            :entry-id-lowercase (str/lower-case entry-id)
            :entry-title (str/trim entry-title)
            :entry-title-lowercase (str/trim (str/lower-case entry-title))
            :provider-id provider-id
            :provider-id-lowercase (str/lower-case provider-id)
            :short-name short-name
            :short-name-lowercase (util/safe-lowercase short-name)
            :version-id version-id
            :version-id-lowercase (util/safe-lowercase version-id)
            :parsed-version-id-lowercase (util/safe-lowercase parsed-version-id)
            :deleted (boolean deleted)
            :revision-date revision-date
            :access-value access-value
            :processing-level-id processing-level-id
            :processing-level-id-lowercase (util/safe-lowercase processing-level-id)
            :collection-data-type collection-data-type
            :collection-data-type-lowercase (when collection-data-type
                                              (if (sequential? collection-data-type)
                                                (map str/lower-case collection-data-type)
                                                (str/lower-case collection-data-type)))
            :platform-sn platform-short-names
            :platform-sn-lowercase  (map str/lower-case platform-short-names)

            ;; hierarchical fields
            :platforms platforms-nested
            :instruments instruments-nested
            :archive-centers archive-centers
            :data-centers data-centers
            :temporals temporal-extents
            :temporal-ranges (mapcat (fn [extent]
                                       [(:start-date extent)
                                        (:end-date extent)])
                                     temporal-extents-ranges)
            ;; added so that we can respect all collection temporal ranges in search
            ;; when limit_to_granules is set and there are no granules for the collection.
            :limit-to-granules-temporals
            (if granule-start-date
              [{:start-date (index-util/date->elastic granule-start-date)
                :end-date (index-util/date->elastic granule-end-date)}]
              temporal-extents)
            :science-keywords (map #(sk/science-keyword->elastic-doc kms-index %)
                                   (:ScienceKeywords collection))
            :location-keywords (map #(clk/location-keyword->elastic-doc kms-index %)
                                    (:LocationKeywords collection))

            :instrument-sn instrument-short-names
            :instrument-sn-lowercase  (map str/lower-case instrument-short-names)
            :sensor-sn sensor-short-names
            :sensor-sn-lowercase  (map str/lower-case sensor-short-names)
            :authors authors
            :authors-lowercase (map str/lower-case authors)
            :project-sn project-short-names
            :project-sn-lowercase  (map str/lower-case project-short-names)
            :two-d-coord-name two-d-coord-names
            :two-d-coord-name-lowercase  (map str/lower-case two-d-coord-names)
            :spatial-keyword spatial-keywords
            :spatial-keyword-lowercase  (map str/lower-case spatial-keywords)
            :attributes (attrib/aas->elastic-docs collection)
            :science-keywords-flat (sk/flatten-science-keywords collection)
            :personnel (json/generate-string personnel)
            :archive-center archive-center-names
            :archive-center-lowercase (map str/lower-case archive-center-names)
            :data-center data-center-names
            :data-center-lowercase (map str/lower-case data-center-names)
            :downloadable downloadable
            :browsable browsable
            :atom-links atom-links
            :summary summary
            :metadata-format (name (mt/format-key format))
            :related-urls (map json/generate-string opendata-related-urls)
            :has-opendap-url (not (empty? (filter opendap-util/opendap-url? related-urls)))
            :publication-references opendata-references
            :collection-citations (map json/generate-string opendata-citations)
            :update-time update-time
            :index-time index-time
            :insert-time insert-time
            :created-at created-at
            :coordinate-system coordinate-system

            ;; fields added to support keyword searches
            :keyword (k/create-keywords-field concept-id collection
                                              {:platform-long-names platform-long-names
                                               :instrument-long-names instrument-long-names
                                               :entry-id entry-id})
            :platform-ln-lowercase (map str/lower-case platform-long-names)
            :instrument-ln-lowercase (map str/lower-case instrument-long-names)
            :sensor-ln-lowercase (map str/lower-case sensor-long-names)
            :project-ln-lowercase (map str/lower-case project-long-names)
            :temporal-keyword-lowercase (map str/lower-case temporal-keywords)

            ;; tags
            :tags tags
            ;; tag-data saved in elasticsearch for retrieving purpose in the format of:
            ;; {"org.ceos.wgiss.cwic.native_id": {"associationDate":"2015-01-01T00:00:00.0Z",
            ;;                                    "data": "Global Maps of Atmospheric Nitrogen Deposition, 1860, 1993, and 2050"},
            ;;  "org.ceos.wgiss.cwic.data_provider": {"associationDate":"2015-01-01T00:00:00.0Z",
            ;;                                        "data": "NASA"},
            ;;  "org.ceos.wgiss.cwic.cwic_status": {"associationDate":"2015-01-01T00:00:00.0Z",
            ;;                                      "data": "prod"}}
            :tags-gzip-b64 (when (seq tag-associations)
                             (util/string->gzip-base64
                              (pr-str
                               (into {} (for [ta tag-associations]
                                          [(:tag-key ta) (util/remove-nil-keys
                                                          {:data (:data ta)})])))))

            :processing-level-id-lowercase-humanized (-> humanized-values
                                                         :processing-level-id-humanized
                                                         first
                                                         :value-lowercase)
            :associations-gzip-b64 (associations->gzip-base64-str
                                    variable-associations service-associations)
            :usage-relevancy-score 0}

           (variable-service-associations->elastic-docs
            context variable-associations service-associations)
           (collection-temporal-elastic context concept-id collection)
           (spatial/collection-orbit-parameters->elastic-docs collection)
           (spatial->elastic collection)
           (sk/science-keywords->facet-fields collection)
           humanized-values
           (metrics/collection-community-usage-score context collection parsed-version-id))))

(defn- get-elastic-doc-for-tombstone-collection
  "Get the subset of elastic field values that apply to a tombstone index operation."
  [context concept]
  (let [{{:keys [short-name version-id entry-id entry-title]} :extra-fields
         :keys [concept-id revision-id provider-id user-id
                native-id revision-date deleted format]} concept
        ;; only used to get default ACLs for tombstones
        tombstone-umm (umm-collection/map->UMM-C {:EntryTitle entry-title})
        tombstone-umm (merge {:concept-id concept-id} tombstone-umm)
        tombstone-permitted-group-ids (collection-util/get-coll-permitted-group-ids
                                       context provider-id tombstone-umm)]
    {:concept-id concept-id
     :revision-id revision-id
     :concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
     :native-id native-id
     :native-id-lowercase (util/safe-lowercase native-id)
     :user-id user-id
     :short-name short-name
     :short-name-lowercase (util/safe-lowercase short-name)
     :entry-id entry-id
     :entry-id-lowercase (util/safe-lowercase entry-id)
     :entry-title entry-title
     :entry-title-lowercase (util/safe-lowercase entry-title)
     :version-id version-id
     :version-id-lowercase (util/safe-lowercase version-id)
     :deleted (boolean deleted)
     :provider-id provider-id
     :provider-id-lowercase (util/safe-lowercase provider-id)
     :revision-date revision-date
     :metadata-format (when format (name (mt/format-key format)))
     :permitted-group-ids tombstone-permitted-group-ids}))

(defmethod es/parsed-concept->elastic-doc :collection
  [context concept umm-collection]
  (if (:deleted concept)
    (get-elastic-doc-for-tombstone-collection context concept)
    (get-elastic-doc-for-full-collection context
                                         concept
                                         umm-collection)))
