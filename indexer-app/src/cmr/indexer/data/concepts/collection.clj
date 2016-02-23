(ns cmr.indexer.data.concepts.collection
  "Contains functions to parse and convert collection concept"
  (:require [clojure.string :as str]
            [clj-time.format :as f]
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]
            [cmr.indexer.services.index-service :as idx]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.common.mime-types :as mt]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.umm.related-url-helper :as ru]
            [cmr.umm.start-end-date :as sed]
            [cmr.indexer.data.concepts.attribute :as attrib]
            [cmr.indexer.data.concepts.science-keyword :as sk]
            [cmr.indexer.data.concepts.platform :as platform]
            [cmr.indexer.data.concepts.instrument :as instrument]
            [cmr.indexer.data.concepts.spatial :as spatial]
            [cmr.indexer.data.concepts.keyword :as k]
            [cmr.indexer.data.concepts.organization :as org]
            [cmr.acl.core :as acl]
            [cmr.common.concepts :as concepts]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.umm.collection :as umm-c]
            [cmr.umm.collection.entry-id :as eid]
            [cmr.common-app.services.kms-fetcher :as kf])
  (:import cmr.spatial.mbr.Mbr))

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

(defn- get-tag-associations-for-collection
  "Get all the tag associations for a collection"
  [context concept]
  (let [params {:associated-concept-id (:concept-id concept)
                :exclude-metadata true
                :latest true}
        tag-associations (mdb/find-concepts context params :tag-association)]
    ;; we only want the tag associations that have no associated revision id or one equal to the
    ;; revision of this collection
    (filter (fn [ta] (let [rev-id (:associated-revision-id ta)]
                       (or (nil? rev-id)
                           (= (rev-id (:revision-id concept))))))
            tag-associations)))

(defn- get-elastic-doc-for-full-collection
  "Get all the fields for a normal collection index operation."
  [context concept collection]
  (let [{:keys [concept-id revision-id provider-id user-id
                native-id revision-date deleted format extra-fields]} concept
        {{:keys [short-name long-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-title summary temporal related-urls spatial-keywords associated-difs
                temporal-keywords access-value personnel distribution]} collection
        collection-data-type (if (= "NEAR_REAL_TIME" collection-data-type)
                               ;; add in all the aliases for NEAR_REAL_TIME
                               (concat [collection-data-type] k/nrt-aliases)
                               collection-data-type)
        entry-id (eid/entry-id short-name version-id)
        personnel (person-with-email personnel)
        platforms (:platforms collection)
        platform-short-names (->> (map :short-name platforms)
                                  (map str/trim))
        gcmd-keywords-map (kf/get-gcmd-keywords-map context)
        platforms-nested (map #(platform/platform-short-name->elastic-doc gcmd-keywords-map %)
                              platform-short-names)
        platform-long-names (->> (distinct (keep :long-name (concat platforms platforms-nested)))
                                 (map str/trim))
        instruments (mapcat :instruments platforms)
        instrument-short-names (->> (keep :short-name instruments)
                                    (map str/trim))
        instruments-nested (map #(instrument/instrument-short-name->elastic-doc gcmd-keywords-map %)
                                instrument-short-names)
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
        archive-center-names (map str/trim
                                  (org/extract-data-center-names collection :archive-center))
        archive-centers (map #(org/data-center-short-name->elastic-doc gcmd-keywords-map %)
                             archive-center-names)
        data-center-names (map str/trim (org/extract-data-center-names collection))
        data-centers (map #(org/data-center-short-name->elastic-doc gcmd-keywords-map %)
                          data-center-names)
        start-date (sed/start-date :collection temporal)
        end-date (sed/end-date :collection temporal)
        atom-links (map json/generate-string (ru/atom-links related-urls))
        ;; not empty is used below to get a real true/false value
        downloadable (not (empty? (ru/downloadable-urls related-urls)))
        browsable (not (empty? (ru/browse-urls related-urls)))
        update-time (get-in collection [:data-provider-timestamps :update-time])
        update-time (f/unparse (f/formatters :date-time) update-time)
        insert-time (get-in collection [:data-provider-timestamps :insert-time])
        insert-time (f/unparse (f/formatters :date-time) insert-time)
        spatial-representation (get-in collection [:spatial-coverage :spatial-representation])
        permitted-group-ids (acl/get-coll-permitted-group-ids context provider-id collection)
        tag-associations (get-tag-associations-for-collection context concept)
        elastic-version-id (apply max (:transaction-id concept) (map :transaction-id tag-associations))]
    (merge {:elastic-version-id elastic-version-id
            :concept-id concept-id
            :revision-id revision-id
            :concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
            :native-id native-id
            :native-id.lowercase (str/lower-case native-id)
            :user-id user-id
            :permitted-group-ids permitted-group-ids
            :entry-id entry-id
            :entry-id.lowercase (str/lower-case entry-id)
            :entry-title entry-title
            :entry-title.lowercase (str/lower-case entry-title)
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
                                   (:science-keywords collection))

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
            :attributes (attrib/psas->elastic-docs collection)
            :science-keywords-flat (sk/flatten-science-keywords collection)
            :personnel (json/generate-string personnel)
            :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
            :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))
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
                                               :instrument-long-names instrument-long-names})
            :long-name.lowercase (when long-name (str/lower-case long-name))
            :platform-ln.lowercase (map str/lower-case platform-long-names)
            :instrument-ln.lowercase (map str/lower-case instrument-long-names)
            :sensor-ln.lowercase (map str/lower-case sensor-long-names)
            :project-ln.lowercase (map str/lower-case project-long-names)
            :temporal-keyword.lowercase (map str/lower-case temporal-keywords)}
           (get-in collection [:spatial-coverage :orbit-parameters])
           (spatial->elastic collection)
           (sk/science-keywords->facet-fields collection))))


(defn- get-elastic-doc-for-tombstone-collection
  "Get the subset of elastic field values that apply to a tombstone index operation."
  [context concept]
  (let [{{:keys [short-name version-id entry-id entry-title]} :extra-fields
         :keys [concept-id revision-id provider-id user-id
                native-id revision-date deleted format]} concept
        ;; only used to get default ACLs for tombstones
        tombstone-umm (umm-c/map->UmmCollection {:entry-title entry-title})
        tombstone-permitted-group-ids (acl/get-coll-permitted-group-ids context
                                                                        provider-id tombstone-umm)
        {:keys [access-value]} tombstone-umm]
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
     :permitted-group-ids tombstone-permitted-group-ids
     :access-value access-value}))

(defmethod es/concept->elastic-doc :collection
  [context concept collection]
  (if (:deleted concept)
    (get-elastic-doc-for-tombstone-collection context concept)
    (get-elastic-doc-for-full-collection context concept collection)))
