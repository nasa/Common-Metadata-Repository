(ns cmr.indexer.data.concepts.collection
  "Contains functions to parse and convert collection concept"
  (:require
    [camel-snake-kebab.core :as csk]
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clojure.string :as str]
    [cmr.acl.acl-fetcher :as acl-fetcher]
    [cmr.acl.core :as acl]
    [cmr.common-app.humanizer :as humanizer]
    [cmr.common-app.services.kms-fetcher :as kf]
    [cmr.common.concepts :as concepts]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.time-keeper :as tk]
    [cmr.common.util :as util]
    [cmr.elastic-utils.index-util :as index-util]
    [cmr.indexer.data.collection-granule-aggregation-cache :as cgac]
    [cmr.indexer.data.concepts.attribute :as attrib]
    [cmr.indexer.data.concepts.instrument :as instrument]
    [cmr.indexer.data.concepts.keyword :as k]
    [cmr.indexer.data.concepts.location-keyword :as lk]
    [cmr.indexer.data.concepts.organization :as org]
    [cmr.indexer.data.concepts.platform :as platform]
    [cmr.indexer.data.concepts.science-keyword :as sk]
    [cmr.indexer.data.concepts.spatial :as spatial]
    [cmr.indexer.data.concepts.tag :as tag]
    [cmr.indexer.data.elasticsearch :as es]
    [cmr.indexer.data.humanizer-fetcher :as hf]
    [cmr.indexer.services.index-service :as idx]
    [cmr.umm-spec.time :as spec-time]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm.acl-matchers :as umm-matchers]
    [cmr.umm.collection.entry-id :as eid]
    [cmr.umm.related-url-helper :as ru]
    [cmr.umm.start-end-date :as sed]
    [cmr.umm.umm-collection :as umm-c])
  (:import
    (cmr.spatial.mbr Mbr)))

(defn spatial->elastic
  [collection]
  (when-let [geometries (seq (get-in collection [:spatial-coverage :geometries]))]
    (let [sr (get-in collection [:spatial-coverage :spatial-representation])]
      (cond
        (or (= sr :geodetic) (= sr :cartesian))
        (spatial/spatial->elastic-docs sr collection)

        :else
        (errors/internal-error! (str "Unknown spatial representation [" sr "]"))))))

(defn- person->email-contact
  "Return an email contact for the Personnel record or nil if none is available."
  [person]
  (first (filter (fn [contact]
                   (= :email
                      (:type contact)))
                 (:contacts person))))

(defn person-with-email
  "Returns the first Personnel record for the list with an email contact or
  nil if none exists."
  [personnel]
  (first (filter person->email-contact personnel)))

(defn- collection-temporal-elastic
  "Returns a map of collection temporal fields for indexing in Elasticsearch."
  [context concept-id collection]
  (let [start-date (spec-time/collection-start-date collection)
        end-date (spec-time/normalized-end-date collection)
        {:keys [granule-start-date granule-end-date]} (cgac/get-coll-gran-aggregates context concept-id)
        last-3-days (t/interval (t/minus (tk/now) (t/days 3)) (tk/now))
        granule-end-date (if (and granule-end-date (t/within? last-3-days granule-end-date))
                           ;; If the granule end date is within the last 3 days we indicate that
                           ;; the collection has no end date. This allows NRT collections to be
                           ;; found even if the collection has been reindexed recently.
                          nil
                          granule-end-date)
        coll-start (index-util/date->elastic start-date)
        coll-end (index-util/date->elastic end-date)]
    (merge {:start-date coll-start
            :end-date coll-end}
           (or (when granule-start-date
                 {:granule-start-date (index-util/date->elastic granule-start-date)
                  :granule-end-date (index-util/date->elastic granule-end-date)})
               ;; Use the collection start and end date if there are no granule start and end dates.
               {:granule-start-date coll-start
                :granule-end-date coll-end}))))

(defn- add-humanized-lowercase
  "Adds a :value.lowercase field to a humanized object"
  [obj]
  (assoc obj :value.lowercase (str/lower-case (:value obj))))

(defn- select-indexable-humanizer-fields
  "Selects the fields from humanizers that can be indexed."
  [value]
  (select-keys value [:value :priority]))

(defn- extract-humanized-elastic-fields
  "Descends into the humanized collection extracting values at the given humanized
  field path and returns a map of humanized and lowercase humanized elastic fields
  for that path"
  [humanized-collection path base-es-field]
  (let [prefix (subs (str base-es-field) 1)
        field (keyword (str prefix ".humanized2"))
        value-with-priorities (util/get-in-all humanized-collection path)
        value-with-priorities (if (sequential? value-with-priorities)
                                (map select-indexable-humanizer-fields value-with-priorities)
                                (select-indexable-humanizer-fields value-with-priorities))
        value-with-lowercases (if (sequential? value-with-priorities)
                                (map add-humanized-lowercase
                                     (distinct (filter :value value-with-priorities)))
                                (add-humanized-lowercase value-with-priorities))]
    {field value-with-lowercases}))

(defn- collection-humanizers-elastic
  "Given a umm-spec collection, returns humanized elastic search fields"
  [context collection]
  (let [humanized (humanizer/umm-collection->umm-collection+humanizers
                    collection (hf/get-humanizer-instructions context))
        extract-fields (partial extract-humanized-elastic-fields humanized)]
    (merge
     {:science-keywords.humanized (map sk/humanized-science-keyword->elastic-doc
                                   (:science-keywords humanized))}
     (extract-fields [:platforms :cmr.humanized/short-name] :short-name)
     (extract-fields [:platforms :instruments :cmr.humanized/short-name] :short-name)
     (extract-fields [:projects :cmr.humanized/short-name] :short-name)
     (extract-fields [:product :cmr.humanized/processing-level-id] :processing-level-id)
     (extract-fields [:organizations :cmr.humanized/short-name] :short-name))))

(defn- get-coll-permitted-group-ids
  "Returns the groups ids (group guids, 'guest', 'registered') that have permission to read
  this collection"
  [context provider-id coll]
  (->> (acl-fetcher/get-acls context [:catalog-item])
       ;; Find only acls that are applicable to this collection
       (filter (partial umm-matchers/coll-applicable-acl? provider-id coll))
       ;; Get the permissions they grant
       (mapcat :aces)
       ;; Find permissions that grant read
       (filter #(some (partial = :read) (:permissions %)))
       ;; Get the group guids or user type of those permissions
       (map #(or (:group-guid %) (some-> % :user-type name)))
       distinct))

(defn- get-elastic-doc-for-full-collection
  "Get all the fields for a normal collection index operation."
  [context concept collection umm-spec-collection]
  (let [{:keys [concept-id revision-id provider-id user-id
                native-id revision-date deleted format extra-fields tag-associations]} concept
        {{:keys [short-name long-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-title summary related-urls spatial-keywords associated-difs
                access-value personnel distribution]} collection
        temporal-keywords (:TemporalKeywords umm-spec-collection) 
        collection-data-type (if (= "NEAR_REAL_TIME" collection-data-type)
                               ;; add in all the aliases for NEAR_REAL_TIME
                               (concat [collection-data-type] k/nrt-aliases)
                               collection-data-type)
        entry-id (eid/entry-id short-name version-id)
        personnel (person-with-email personnel)
        platforms (:platforms collection)
        gcmd-keywords-map (kf/get-gcmd-keywords-map context)
        platforms-nested (map #(platform/platform-short-name->elastic-doc gcmd-keywords-map %)
                              (map :short-name platforms))
        platform-short-names (->> (map :short-name platforms-nested)
                                  (map str/trim))
        platform-long-names (->> (distinct (keep :long-name (concat platforms platforms-nested)))
                                 (map str/trim))
        instruments (mapcat :instruments platforms)
        instruments-nested (map #(instrument/instrument-short-name->elastic-doc gcmd-keywords-map %)
                                (keep :short-name instruments))
        instrument-short-names (->> instruments-nested
                                    (map :short-name)
                                    (map str/trim))
        instrument-long-names (->> (distinct (keep :long-name
                                                   (concat instruments instruments-nested)))
                                   (map str/trim))
        sensors (mapcat :sensors instruments)
        sensor-short-names (keep :short-name sensors)
        sensor-long-names (keep :long-name sensors)
        project-short-names (->> (map :short-name (:projects collection))
                                 (map str/trim))
        project-long-names (->> (keep :long-name (:projects collection))
                                (map str/trim))
        two-d-coord-names (map :name (:two-d-coordinate-systems collection))
        archive-centers (map #(org/data-center-short-name->elastic-doc gcmd-keywords-map %)
                             (map str/trim
                                  (org/extract-data-center-names collection :archive-center)))
        ;; get the normalized names back
        archive-center-names (keep :short-name archive-centers)
        data-centers (map #(org/data-center-short-name->elastic-doc gcmd-keywords-map %)
                          (map str/trim (org/extract-data-center-names collection)))
        data-center-names (keep :short-name data-centers)
        atom-links (map json/generate-string (ru/atom-links related-urls))
        ;; not empty is used below to get a real true/false value
        downloadable (not (empty? (ru/downloadable-urls related-urls)))
        browsable (not (empty? (ru/browse-urls related-urls)))
        update-time (get-in collection [:data-provider-timestamps :update-time])
        update-time (index-util/date->elastic update-time)
        insert-time (get-in collection [:data-provider-timestamps :insert-time])
        insert-time (index-util/date->elastic insert-time)
        spatial-representation (get-in collection [:spatial-coverage :spatial-representation])
        permitted-group-ids (get-coll-permitted-group-ids context provider-id collection)]
    (merge {:concept-id concept-id
            :revision-id revision-id
            :concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
            :native-id native-id
            :native-id.lowercase (str/lower-case native-id)
            :user-id user-id
            :permitted-group-ids permitted-group-ids
            ;; If there's an entry in the collection granule aggregates then the collection has granules.
            :has-granules (some? (cgac/get-coll-gran-aggregates context concept-id))
            :entry-id entry-id
            :entry-id.lowercase (str/lower-case entry-id)
            :entry-title (str/trim entry-title)
            :entry-title.lowercase (str/trim (str/lower-case entry-title))
            :provider-id provider-id
            :provider-id.lowercase (str/lower-case provider-id)
            :short-name short-name
            :short-name.lowercase (when short-name (str/lower-case short-name))
            :version-id version-id
            :version-id.lowercase (when version-id (str/lower-case version-id))
            :deleted (boolean deleted)
            :revision-date2 revision-date
            :access-value access-value
            :processing-level-id processing-level-id
            :processing-level-id.lowercase (when processing-level-id (str/lower-case processing-level-id))
            :collection-data-type collection-data-type
            :collection-data-type.lowercase (when collection-data-type
                                              (if (sequential? collection-data-type)
                                                (map str/lower-case collection-data-type)
                                                (str/lower-case collection-data-type)))
            :platform-sn platform-short-names
            :platform-sn.lowercase  (map str/lower-case platform-short-names)

            ;; hierarchical fields
            :platforms platforms-nested
            :instruments instruments-nested
            :archive-centers archive-centers
            :data-centers data-centers
            :science-keywords (map #(sk/science-keyword->elastic-doc gcmd-keywords-map %)
                                   (:ScienceKeywords umm-spec-collection))
            :location-keywords (map #(lk/location-keyword->elastic-doc gcmd-keywords-map %)
                                   (:LocationKeywords umm-spec-collection))

            :instrument-sn instrument-short-names
            :instrument-sn.lowercase  (map str/lower-case instrument-short-names)
            :sensor-sn sensor-short-names
            :sensor-sn.lowercase  (map str/lower-case sensor-short-names)
            :project-sn2 project-short-names
            :project-sn2.lowercase  (map str/lower-case project-short-names)
            :two-d-coord-name two-d-coord-names
            :two-d-coord-name.lowercase  (map str/lower-case two-d-coord-names)
            :spatial-keyword spatial-keywords
            :spatial-keyword.lowercase  (map str/lower-case spatial-keywords)
            :attributes (attrib/aas->elastic-docs umm-spec-collection)
            :science-keywords-flat (sk/flatten-science-keywords collection)
            :personnel (json/generate-string personnel)
            :archive-center archive-center-names
            :archive-center.lowercase (map str/lower-case archive-center-names)
            :data-center data-center-names
            :data-center.lowercase (map str/lower-case data-center-names)
            :downloadable downloadable
            :browsable browsable
            :atom-links atom-links
            :summary summary
            :metadata-format (name (mt/format-key format))
            :related-urls (map json/generate-string related-urls)
            :update-time update-time
            :insert-time insert-time
            :associated-difs associated-difs
            :associated-difs.lowercase (map str/lower-case associated-difs)
            :coordinate-system (when spatial-representation
                                 (csk/->SCREAMING_SNAKE_CASE_STRING spatial-representation))

            ;; fields added to support keyword searches
            :keyword (k/create-keywords-field concept-id collection
                                              {:platform-long-names platform-long-names
                                               :instrument-long-names instrument-long-names
                                               :entry-id entry-id})
            :long-name.lowercase (when long-name (str/lower-case long-name))
            :platform-ln.lowercase (map str/lower-case platform-long-names)
            :instrument-ln.lowercase (map str/lower-case instrument-long-names)
            :sensor-ln.lowercase (map str/lower-case sensor-long-names)
            :project-ln.lowercase (map str/lower-case project-long-names)
            :temporal-keyword.lowercase (map str/lower-case temporal-keywords)

            ;; tags
            :tags (map tag/tag-association->elastic-doc tag-associations)
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
                                                             {:data (:data ta)})])))))}
           (collection-temporal-elastic context concept-id umm-spec-collection)
           (get-in collection [:spatial-coverage :orbit-parameters])
           (spatial->elastic collection)
           (sk/science-keywords->facet-fields collection)
           (collection-humanizers-elastic context umm-spec-collection))))

(defn- get-elastic-doc-for-tombstone-collection
  "Get the subset of elastic field values that apply to a tombstone index operation."
  [context concept]
  (let [{{:keys [short-name version-id entry-id entry-title]} :extra-fields
         :keys [concept-id revision-id provider-id user-id
                native-id revision-date deleted format]} concept
        ;; only used to get default ACLs for tombstones
        tombstone-umm (umm-c/map->UmmCollection {:entry-title entry-title})
        tombstone-permitted-group-ids (get-coll-permitted-group-ids context
                                                                    provider-id tombstone-umm)]
    {:concept-id concept-id
     :revision-id revision-id
     :concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
     :native-id native-id
     :native-id.lowercase (str/lower-case native-id)
     :user-id user-id
     :short-name short-name
     :short-name.lowercase (when short-name (str/lower-case short-name))
     :entry-id entry-id
     :entry-id.lowercase (str/lower-case entry-id)
     :entry-title entry-title
     :entry-title.lowercase (str/lower-case entry-title)
     :version-id version-id
     :version-id.lowercase (when version-id (str/lower-case version-id))
     :deleted (boolean deleted)
     :provider-id provider-id
     :provider-id.lowercase (str/lower-case provider-id)
     :revision-date2 revision-date
     :metadata-format (name (mt/format-key format))
     :permitted-group-ids tombstone-permitted-group-ids}))

(defmethod es/parsed-concept->elastic-doc :collection
  [context concept umm-legacy-collection]
  (if (:deleted concept)
    (get-elastic-doc-for-tombstone-collection context concept)
    (let [umm-spec-collection (umm-spec/parse-metadata context concept)]
      (get-elastic-doc-for-full-collection context
                                           concept
                                           umm-legacy-collection
                                           umm-spec-collection))))
