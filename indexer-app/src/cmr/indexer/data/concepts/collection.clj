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
    [cmr.indexer.data.concepts.data-center :as dc]
    [cmr.indexer.data.concepts.instrument :as instrument]
    [cmr.indexer.data.concepts.keyword :as k]
    [cmr.indexer.data.concepts.location-keyword :as clk]
    [cmr.indexer.data.concepts.platform :as platform]
    [cmr.indexer.data.concepts.science-keyword :as sk]
    [cmr.indexer.data.concepts.spatial :as spatial]
    [cmr.indexer.data.concepts.tag :as tag]
    [cmr.indexer.data.elasticsearch :as es]
    [cmr.indexer.data.humanizer-fetcher :as hf]
    [cmr.indexer.services.index-service :as idx]
    [cmr.umm-spec.date-util :as date-util]
    [cmr.umm-spec.location-keywords :as lk]
    [cmr.umm-spec.related-url :as ru]
    [cmr.umm-spec.time :as spec-time]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm-spec.util :as su]
    [cmr.umm.acl-matchers :as umm-matchers]
    [cmr.umm.collection.entry-id :as eid]
    [cmr.umm.start-end-date :as sed]
    [cmr.umm.umm-collection :as umm-c])
  (:import
    (cmr.spatial.mbr Mbr)))

(defn spatial->elastic
  [collection]
  (when-let [coord-sys (spatial/get-collection-coordinate-system collection)]
    (cond
      (or (= coord-sys :geodetic) (= coord-sys :cartesian))
      (spatial/collection-spatial->elastic-docs coord-sys collection)

      :else
      (errors/internal-error! (str "Unknown spatial representation [" coord-sys "]")))))

(defn- email-contact?
  "Return true if the given person has an email as contact info."
  [person]
  (some #(= "Email" (:Type %))
        (get-in person [:ContactInformation :ContactMechanisms])))

(defn- data-center-contacts
  "Returns the data center contacts with ContactInformation added if it doesn't have contact info"
  [data-center]
  (let [contacts (concat (:ContactPersons data-center) (:ContactGroups data-center))]
    (map (fn [contact]
           (if (:ContactInformation contact)
             contact
             (assoc contact :ContactInformation (:ContactInformation data-center))))
         contacts)))

(defn opendata-email-contact
  "Returns the opendata email contact info for the given collection, it is just the first email
  contact info found in the ContactPersons, ContactGroups or DataCenters."
  [collection]
  (let [{:keys [ContactPersons ContactGroups DataCenters]} collection
        contacts (concat ContactPersons ContactGroups (mapcat data-center-contacts DataCenters))
        email-contact (some #(when (email-contact? %) %) contacts)]
    (when email-contact
      (let [email (some #(when (= "Email" (:Type %)) (:Value %))
                        (get-in email-contact [:ContactInformation :ContactMechanisms]))
            email-contacts (when email [{:type :email :value email}])]
        {:first-name (:FirstName email-contact)
         :middle-name (:MiddleName email-contact)
         :last-name (:LastName email-contact)
         :roles (:Roles email-contact)
         :contacts email-contacts}))))

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
  (assoc-in-nil-if
   collection
   [:ProcessingLevel :Id]
   (= (get-in collection [:ProcessingLevel :Id]) su/not-provided)))

(defn- sanitize-collection
  "Remove default values to avoid them being indexed"
  [collection]
  (-> collection
   (assoc-nil-if :Platforms (= (:Platforms collection) su/not-provided-platforms))
   sanitize-processing-level-ids
   (assoc-nil-if :DataCenters (= (:DataCenters collection) [su/not-provided-data-center]))))

(defn- collection-humanizers-elastic
  "Given a umm-spec collection, returns humanized elastic search fields"
  [context collection]
  (let [sanitized-collection (sanitize-collection collection)
        humanized (humanizer/umm-collection->umm-collection+humanizers
                    sanitized-collection (hf/get-humanizer-instructions context))
        extract-fields (partial extract-humanized-elastic-fields humanized)]
    (merge
     {:science-keywords.humanized (map sk/humanized-science-keyword->elastic-doc
                                   (:ScienceKeywords humanized))}
     (extract-fields [:Platforms :cmr.humanized/ShortName] :platform-sn)
     (extract-fields [:Platforms :Instruments :cmr.humanized/ShortName] :instrument-sn)
     (extract-fields [:Projects :cmr.humanized/ShortName] :project-sn)
     (extract-fields [:ProcessingLevel :cmr.humanized/Id] :processing-level-id)
     (extract-fields [:DataCenters :cmr.humanized/ShortName] :organization))))

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

(defn- related-url->opendata-related-url
  "Returns the opendata related url for the given collection related url"
  [related-url]
  (let [{:keys [Title Description Relation URLs MimeType FileSize]} related-url
        {:keys [Size Unit]} FileSize
        size (when (or Size Unit) (str Size Unit))]
    ;; The current UMM JSON RelatedUrlType is flawed in that there can be multiple URLs,
    ;; but only a single Title, MimeType and FileSize. This model doesn't make sense.
    ;; Talked to Erich and he said that we are going to change the model.
    ;; So for now, we make the assumption that there is only one URL in each RelatedUrlType.
    {:type (first Relation)
     :sub-type (second Relation)
     :url (first URLs)
     :description Description
     :mime-type MimeType
     :title Title
     :size size}))

(defn- get-elastic-doc-for-full-collection
  "Get all the fields for a normal collection index operation."
  [context concept collection umm-spec-collection]
  (let [{:keys [concept-id revision-id provider-id user-id
                native-id revision-date deleted format extra-fields tag-associations]} concept
        {short-name :ShortName version-id :Version entry-title :EntryTitle
         collection-data-type :CollectionDataType summary :Abstract
         temporal-keywords :TemporalKeywords platforms :Platforms
         related-urls :RelatedUrls} umm-spec-collection
        processing-level-id (get-in umm-spec-collection [:ProcessingLevel :Id])
        processing-level-id (when-not (= su/not-provided processing-level-id)
                              processing-level-id)
        related-urls (when-not (= [su/not-provided-related-url] related-urls) related-urls)
        spatial-keywords (lk/location-keywords->spatial-keywords
                          (:LocationKeywords umm-spec-collection))
        access-value (get-in umm-spec-collection [:AccessConstraints :Value])
        collection-data-type (if (= "NEAR_REAL_TIME" collection-data-type)
                               ;; add in all the aliases for NEAR_REAL_TIME
                               (concat [collection-data-type] k/nrt-aliases)
                               collection-data-type)
        entry-id (eid/entry-id short-name version-id)
        opendata-related-urls (map related-url->opendata-related-url related-urls)
        personnel (opendata-email-contact umm-spec-collection)
        platforms (map util/map-keys->kebab-case
                       (when-not (= su/not-provided-platforms platforms) platforms))
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
        project-short-names (->> (map :ShortName (:Projects umm-spec-collection))
                                 (map str/trim))
        project-long-names (->> (keep :LongName (:Projects umm-spec-collection))
                                (map str/trim))
        two-d-coord-names (map :TilingIdentificationSystemName
                               (:TilingIdentificationSystems umm-spec-collection))
        meaningful-short-name-fn (fn [c]
                                   (when-let [short-name (:short-name c)]
                                     (when (not= su/not-provided short-name)
                                       short-name)))
        archive-centers (map #(dc/data-center-short-name->elastic-doc gcmd-keywords-map %)
                             (map str/trim (dc/extract-archive-center-names umm-spec-collection)))
        ;; get the normalized names back
        archive-center-names (keep meaningful-short-name-fn archive-centers)
        data-centers (map #(dc/data-center-short-name->elastic-doc gcmd-keywords-map %)
                          (map str/trim (dc/extract-data-center-names umm-spec-collection)))
        data-center-names (keep meaningful-short-name-fn data-centers)
        atom-links (map json/generate-string (ru/atom-links related-urls))
        ;; not empty is used below to get a real true/false value
        downloadable (not (empty? (ru/downloadable-urls related-urls)))
        browsable (not (empty? (ru/browse-urls related-urls)))
        update-time (get-in collection [:data-provider-timestamps :update-time])
        update-time (index-util/date->elastic update-time)
        insert-time (get-in collection [:data-provider-timestamps :insert-time])
        insert-time (index-util/date->elastic insert-time)
        coordinate-system (get-in umm-spec-collection [:SpatialExtent :HorizontalSpatialDomain
                                                       :Geometry :CoordinateSystem])
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
            :location-keywords (map #(clk/location-keyword->elastic-doc gcmd-keywords-map %)
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
            :related-urls (map json/generate-string opendata-related-urls)
            :update-time update-time
            :insert-time insert-time
            :coordinate-system coordinate-system

            ;; fields added to support keyword searches
            :keyword (k/create-keywords-field concept-id collection umm-spec-collection
                                              {:platform-long-names platform-long-names
                                               :instrument-long-names instrument-long-names
                                               :entry-id entry-id})
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
           (spatial/collection-orbit-parameters->elastic-docs umm-spec-collection)
           (spatial->elastic umm-spec-collection)
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
