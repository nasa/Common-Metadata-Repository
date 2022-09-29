(ns cmr.indexer.data.concepts.granule
  "Contains functions to parse and convert granule concept"
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.index-util :as index-util]
   [cmr.indexer.data.concepts.attribute :as attrib]
   [cmr.indexer.data.concepts.orbit-calculated-spatial-domain :as ocsd]
   [cmr.indexer.data.concepts.spatial :as spatial]
   [cmr.indexer.data.concepts.track :as track]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm.echo10.spatial :as umm-spatial]
   [cmr.umm.related-url-helper :as ru]
   [cmr.umm.start-end-date :as sed])
  (:import
   (cmr.spatial.mbr Mbr)))

(def parent-collection-cache-key
  "The key to be used for the parent collection cache in the system cache map."
  :parent-collection-cache)

(defn unrecognized-gsr-msg
  "The granule spatial representation (gsr) is not of a known type."
  [gsr]
  (str "Unrecognized granule spatial representation [" gsr "]"))

(defn- fetch-parent-collection
  "Retrieve the parent collection umm from the db"
  [context parent-collection-id]
  (let [concept (mdb/get-latest-concept context parent-collection-id)]
    (assoc (umm-spec/parse-metadata context concept) :concept-id parent-collection-id)))

(defn- get-parent-collection
  [context parent-collection-id]
  (if-let [cache (cache/context->cache context parent-collection-cache-key)]
    (cache/get-value cache parent-collection-id
                     (partial fetch-parent-collection context parent-collection-id))
    (fetch-parent-collection context parent-collection-id)))

(defn orbit->circular-latitude-range
  "Compute a circular latitude range from the start and end latitudes of an orbit."
  [orbit]
  (let [{:keys [^double start-lat ^double end-lat start-direction end-direction]} orbit
        start-lat (if (= :desc start-direction)
                    (- 180.0 start-lat)
                    start-lat)
        end-lat (if (= :desc end-direction)
                  (- 180.0 end-lat)
                  end-lat)
        min (mod start-lat 360.0)
        max (if (= 360.0 (- end-lat start-lat))
              (+ min 360.0)
              (+ min (mod (- end-lat start-lat) 360.0)))]
    [min max]))

(defn spatial->elastic
  [parent-collection granule]
  (when-let [gsr (csk/->kebab-case-keyword (get-in parent-collection [:SpatialExtent :GranuleSpatialRepresentation]))]
    (cond
      (or (= gsr :geodetic) (= gsr :cartesian))
      (spatial/granule-spatial->elastic-docs gsr granule)

      (= gsr :no-spatial)
      nil

      (= gsr :orbit)
      (let [orbit (get-in granule [:spatial-coverage :orbit])
            {:keys [ascending-crossing start-lat start-direction end-lat end-direction]} orbit
            [^double orbit-start-clat ^double orbit-end-clat] (orbit->circular-latitude-range orbit)
            orbit-end-clat-normalized (if (= orbit-end-clat orbit-start-clat)
                                        (+ orbit-end-clat 360.0)
                                        orbit-end-clat)]
        {:orbit-asc-crossing-lon ascending-crossing
         :orbit-asc-crossing-lon-doc-values ascending-crossing
         :orbit-start-clat orbit-start-clat
         :orbit-start-clat-doc-values orbit-start-clat
         :orbit-end-clat orbit-end-clat-normalized
         :orbit-end-clat-doc-values orbit-end-clat-normalized
         :start-lat start-lat
         :start-direction (umm-spatial/key->orbit-direction start-direction)
         :end-lat end-lat
         :end-direction (umm-spatial/key->orbit-direction end-direction)})
      :else
      (errors/throw-service-error :invalid-data (unrecognized-gsr-msg gsr)))))

(def ocsd-fields
  "The fields for orbit calculated spatial domains, in the order that they are stored in the json
  string in the index."
  [:equator-crossing-date-time
   :equator-crossing-longitude
   :orbital-model-name
   :orbit-number
   :start-orbit-number
   :stop-orbit-number])

(defn- ocsd-map->vector
  "Turn a map of orbit crossing spatial domain data into a vector"
  [ocsd]
  (map ocsd ocsd-fields))

(defn- granule->ocsd-json
  "Create a json string from the orbitial calculated spatial domains."
  [umm-granule]
  (map #(json/generate-string
         (ocsd-map->vector %))
       (ocsd/ocsds->elastic-docs umm-granule)))

(defn- granule->elastic-doc
  "Returns elastic json that can be used to insert the given granule concept in elasticsearch."
  [context concept umm-granule]
  (let [{:keys [concept-id revision-id extra-fields native-id provider-id
                revision-date format created-at]} concept
        {:keys [parent-collection-id]} extra-fields
        parent-collection (get-parent-collection context parent-collection-id)
        {:keys [granule-ur data-granule temporal platform-refs project-refs related-urls cloud-cover
                access-value two-d-coordinate-system product-specific-attributes]} umm-granule
        {:keys [size producer-gran-id day-night production-date-time
                feature-ids crid-ids]} data-granule
        {:keys [start-coordinate-1 end-coordinate-1 start-coordinate-2 end-coordinate-2]
         two-d-coord-name :name} two-d-coordinate-system
        platform-short-names (map :short-name platform-refs)
        instrument-refs (mapcat :instrument-refs platform-refs)
        instrument-short-names (remove nil? (map :short-name instrument-refs))
        sensor-refs (mapcat :sensor-refs instrument-refs)
        sensor-short-names (remove nil? (map :short-name sensor-refs))
        start-date (sed/start-date :granule temporal)
        end-date (sed/end-date :granule temporal)
        atom-links (map json/generate-string (ru/atom-links related-urls))
        ocsd-json (granule->ocsd-json umm-granule)
        ;; not empty is used below to get a real true false value
        downloadable (not (empty? (ru/downloadable-urls related-urls)))
        browsable (not (empty? (ru/browse-urls related-urls)))
        update-time (get-in umm-granule [:data-provider-timestamps :update-time])
        update-time (index-util/date->elastic update-time)
        track (get-in umm-granule [:spatial-coverage :track])
        {:keys [ShortName Version EntryTitle]} parent-collection
        granule-spatial-representation (get-in parent-collection
                                               [:SpatialExtent :GranuleSpatialRepresentation])
        concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
        collection-concept-seq-id (:sequence-number (concepts/parse-concept-id parent-collection-id))
        ;; We want to pull cloud cover form additional attributes if the root level cloud cover value is absent
        cloud-cover (or cloud-cover (->> product-specific-attributes
                                         (filter #(= "CLOUD_COVERAGE" (:name %)))
                                         first
                                         :values
                                         first
                                         util/safe-read-string))]
    (merge {:concept-id concept-id
            :revision-id revision-id
            :concept-seq-id (min es/MAX_INT concept-seq-id)
            :concept-seq-id-doc-values (min es/MAX_INT concept-seq-id)
            :concept-seq-id-long concept-seq-id
            :concept-seq-id-long-doc-values concept-seq-id
            :collection-concept-id parent-collection-id
            :collection-concept-id-doc-values parent-collection-id
            :collection-concept-seq-id (min es/MAX_INT collection-concept-seq-id)
            :collection-concept-seq-id-long collection-concept-seq-id
            :collection-concept-seq-id-doc-values (min es/MAX_INT collection-concept-seq-id)
            :collection-concept-seq-id-long-doc-values collection-concept-seq-id

            :entry-title EntryTitle
            :entry-title-lowercase (string/lower-case EntryTitle)
            :entry-title-lowercase-doc-values (string/lower-case EntryTitle)
            :metadata-format (-> format
                                 mt/base-mime-type-of
                                 mt/base-mime-type-to-format
                                 name)
            :update-time update-time
            :coordinate-system (when granule-spatial-representation
                                 (csk/->SCREAMING_SNAKE_CASE_STRING granule-spatial-representation))

            :short-name-lowercase (when ShortName (string/lower-case ShortName))
            :short-name-lowercase-doc-values (when ShortName (string/lower-case ShortName))
            :version-id-lowercase (when Version (string/lower-case Version))
            :version-id-lowercase-doc-values (when Version (string/lower-case Version))

            :native-id native-id
            :native-id-stored native-id
            :native-id-lowercase (string/lower-case native-id)

            :provider-id provider-id
            :provider-id-doc-values provider-id
            :provider-id-lowercase (string/lower-case provider-id)
            :provider-id-lowercase-doc-values (string/lower-case provider-id)

            :granule-ur granule-ur
            :granule-ur-lowercase (string/lower-case granule-ur)
            :producer-gran-id producer-gran-id
            :producer-gran-id-lowercase (when producer-gran-id (string/lower-case producer-gran-id))
            :day-night day-night
            :day-night-doc-values day-night
            :day-night-lowercase (when day-night (string/lower-case day-night))
            :access-value access-value
            :access-value-doc-values access-value

            ;; Provides sorting on a combination of producer granule id and granule ur
            :readable-granule-name-sort (string/lower-case (or producer-gran-id granule-ur))

            :platform-sn platform-short-names
            :platform-sn-lowercase  (map string/lower-case platform-short-names)
            :platform-sn-lowercase-doc-values  (map string/lower-case platform-short-names)
            :instrument-sn instrument-short-names
            :instrument-sn-lowercase  (map string/lower-case instrument-short-names)
            :instrument-sn-lowercase-doc-values  (map string/lower-case instrument-short-names)
            :sensor-sn sensor-short-names
            :sensor-sn-lowercase  (map string/lower-case sensor-short-names)
            :sensor-sn-lowercase-doc-values  (map string/lower-case sensor-short-names)
            :project-refs project-refs
            :project-refs-lowercase (map string/lower-case project-refs)
            :project-refs-lowercase-doc-values (map string/lower-case project-refs)
            :feature-id feature-ids
            :feature-id-lowercase (map string/lower-case feature-ids)
            :crid-id crid-ids
            :crid-id-lowercase (map string/lower-case crid-ids)
            :size size
            :size-doc-values size
            :cloud-cover cloud-cover
            :cloud-cover-doc-values cloud-cover
            :orbit-calculated-spatial-domains (ocsd/ocsds->elastic-docs umm-granule)
            :attributes (attrib/psa-refs->elastic-docs parent-collection umm-granule)
            :revision-date revision-date
            :revision-date-doc-values revision-date
            :revision-date-stored-doc-values revision-date
            :downloadable downloadable
            :browsable browsable
            :created-at (or created-at revision-date)
            :production-date production-date-time
            :start-date (index-util/date->elastic start-date)
            :start-date-doc-values (index-util/date->elastic start-date)
            :end-date (index-util/date->elastic end-date)
            :end-date-doc-values (index-util/date->elastic end-date)
            :two-d-coord-name two-d-coord-name
            :two-d-coord-name-lowercase (when two-d-coord-name (string/lower-case two-d-coord-name))
            :start-coordinate-1 start-coordinate-1
            :end-coordinate-1 end-coordinate-1
            :start-coordinate-2 start-coordinate-2
            :end-coordinate-2 end-coordinate-2
            :start-coordinate-1-doc-values start-coordinate-1
            :end-coordinate-1-doc-values end-coordinate-1
            :start-coordinate-2-doc-values start-coordinate-2
            :end-coordinate-2-doc-values end-coordinate-2
            :atom-links atom-links
            :orbit-calculated-spatial-domains-json ocsd-json
            :cycle (:cycle track)
            :passes (track/passes->elastic-docs track)}
           (spatial->elastic parent-collection umm-granule))))

(defmethod es/parsed-concept->elastic-doc :granule
  [context concept umm-granule]
  (if (:deleted concept)
    concept
    (granule->elastic-doc context concept umm-granule)))
