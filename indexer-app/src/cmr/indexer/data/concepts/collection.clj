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
            [cmr.indexer.data.concepts.spatial :as spatial]
            [cmr.indexer.data.concepts.keyword :as k]
            [cmr.indexer.data.concepts.organization :as org]
            [cmr.acl.core :as acl]
            [cmr.common.concepts :as concepts]
            [cmr.umm.collection :as umm-c])
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

(defn- get-elastic-fields-for-full-collection
  "Get all the fields for a normal collection index operation."
  [context concept collection]
  (let [{:keys [concept-id revision-id provider-id native-id revision-date format]} concept
        {{:keys [short-name long-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-id entry-title summary temporal related-urls spatial-keywords associated-difs
                temporal-keywords access-value personnel distribution]} collection
        collection-data-type (if (= "NEAR_REAL_TIME" collection-data-type)
                               ;; add in all the aliases for NEAR_REAL_TIME
                               (concat [collection-data-type] k/nrt-aliases)
                               collection-data-type)
        personnel (person-with-email personnel)
        platforms (:platforms collection)
        platform-short-names (map :short-name platforms)
        platform-long-names (remove nil? (map :long-name platforms))
        instruments (mapcat :instruments platforms)
        instrument-short-names (remove nil? (map :short-name instruments))
        instrument-long-names (remove nil? (map :long-name instruments))
        sensors (mapcat :sensors instruments)
        sensor-short-names (remove nil? (map :short-name sensors))
        sensor-long-names (remove nil? (map :long-name sensors))
        project-short-names (map :short-name (:projects collection))
        project-long-names (remove nil? (map :long-name (:projects collection)))
        two-d-coord-names (map :name (:two-d-coordinate-systems collection))
        archive-center-val (org/extract-archive-centers collection)
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
        permitted-group-ids (acl/get-coll-permitted-group-ids context provider-id collection)]
    (merge {:concept-id concept-id
            :revision-id revision-id
            :concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
            :native-id native-id
            :native-id.lowercase (str/lower-case native-id)
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
            :revision-date revision-date
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
            :instrument-sn instrument-short-names
            :instrument-sn.lowercase  (map str/lower-case instrument-short-names)
            :sensor-sn sensor-short-names
            :sensor-sn.lowercase  (map str/lower-case sensor-short-names)
            :project-sn project-short-names
            :project-sn.lowercase  (map str/lower-case project-short-names)
            :two-d-coord-name two-d-coord-names
            :two-d-coord-name.lowercase  (map str/lower-case two-d-coord-names)
            :spatial-keyword spatial-keywords
            :spatial-keyword.lowercase  (map str/lower-case spatial-keywords)
            :attributes (attrib/psas->elastic-docs collection)
            :science-keywords (sk/science-keywords->elastic-doc collection)
            :science-keywords-flat (sk/flatten-science-keywords collection)
            :personnel (json/generate-string personnel)
            :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
            :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))
            :archive-center archive-center-val
            :archive-center.lowercase (map str/lower-case archive-center-val)
            :downloadable downloadable
            :browsable browsable
            :atom-links atom-links
            :summary summary
            :metadata-format (name (mt/base-mime-type-to-format format))
            :related-urls (map json/generate-string related-urls)
            :update-time update-time
            :insert-time insert-time
            :associated-difs associated-difs
            :associated-difs.lowercase (map str/lower-case associated-difs)
            :coordinate-system (when spatial-representation
                                 (csk/->SCREAMING_SNAKE_CASE_STRING spatial-representation))
            ;; fields added to support keyword searches
            :keyword (k/create-keywords-field concept-id collection)
            :long-name.lowercase (when long-name (str/lower-case long-name))
            :platform-ln.lowercase (map str/lower-case platform-long-names)
            :instrument-ln.lowercase (map str/lower-case instrument-long-names)
            :sensor-ln.lowercase (map str/lower-case sensor-long-names)
            :project-ln.lowercase (map str/lower-case project-long-names)
            :temporal-keyword.lowercase (map str/lower-case temporal-keywords)}
           (get-in collection [:spatial-coverage :orbit-parameters])
           (spatial->elastic collection)
           (sk/science-keywords->facet-fields collection))))


(defn- get-elastic-fields-for-tombstone-collection
  "Get the subset of elastic field values that apply to a tombstone index operation."
  [context concept]
  (let [{{:keys [short-name version-id entry-id entry-title]} :extra-fields
         :keys [concept-id revision-id provider-id native-id revision-date deleted format]} concept
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
     :revision-date revision-date
     :metadata-format (name (mt/base-mime-type-to-format format))
     :permitted-group-ids tombstone-permitted-group-ids
     :access-value access-value}))

(defmethod es/concept->elastic-doc :collection
  [context concept collection]
  (if (:deleted concept)
    (get-elastic-fields-for-tombstone-collection context concept)
    (get-elastic-fields-for-full-collection context concept collection)))

