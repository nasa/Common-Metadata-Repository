(ns cmr.indexer.data.concepts.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.umm.core :as umm]
            [cmr.umm.related-url-helper :as ru]
            [cmr.umm.echo10.spatial :as umm-spatial]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cmr.umm.start-end-date :as sed]
            [cmr.indexer.data.concepts.attribute :as attrib]
            [cmr.indexer.data.concepts.orbit-calculated-spatial-domain :as ocsd]
            [cmr.indexer.data.concepts.spatial :as spatial]
            [cmr.common.cache :as cache]
            [cmr.common.concepts :as concepts])
  (:import cmr.spatial.mbr.Mbr))

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
    (assoc (umm/parse-concept concept) :concept-id parent-collection-id)))

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
  (when-let [gsr (get-in parent-collection [:spatial-coverage :granule-spatial-representation])]
    (cond
      (or (= gsr :geodetic) (= gsr :cartesian))
      (let [geometries (seq (get-in granule [:spatial-coverage :geometries]))]
        (spatial/spatial->elastic-docs gsr granule))

      (= gsr :no-spatial)
      nil

      (= gsr :orbit)
      (let [orbit (get-in granule [:spatial-coverage :orbit])
            {:keys [ascending-crossing start-lat start-direction end-lat end-direction]} orbit
            [^double orbit-start-clat ^double orbit-end-clat] (orbit->circular-latitude-range orbit)]
        {:orbit-asc-crossing-lon ascending-crossing
         :orbit-start-clat orbit-start-clat
         :orbit-end-clat (if (= orbit-end-clat orbit-start-clat)
                           (+ orbit-end-clat 360.0)
                           orbit-end-clat)
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

(defmethod es/concept->elastic-doc :granule
  [context concept umm-granule]
  (let [{:keys [concept-id extra-fields provider-id revision-date format]} concept
        {:keys [parent-collection-id granule-ur]} extra-fields
        parent-collection (get-parent-collection context parent-collection-id)
        {:keys [data-granule temporal platform-refs project-refs related-urls cloud-cover
                access-value two-d-coordinate-system]} umm-granule
        {:keys [size producer-gran-id day-night]} data-granule
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
        update-time (f/unparse (f/formatters :date-time) update-time)
        {:keys [short-name version-id]} (:product parent-collection)
        granule-spatial-representation (get-in parent-collection [:spatial-coverage :granule-spatial-representation])]
    (merge {:concept-id concept-id
            :concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
            :collection-concept-id parent-collection-id
            :collection-concept-seq-id (:sequence-number (concepts/parse-concept-id parent-collection-id))

            :entry-title (:entry-title parent-collection)
            :metadata-format (name (mt/base-mime-type-to-format format))
            :update-time update-time
            :coordinate-system (when granule-spatial-representation
                                 (csk/->SCREAMING_SNAKE_CASE_STRING granule-spatial-representation))

            :entry-title.lowercase (s/lower-case (:entry-title parent-collection))
            :short-name.lowercase (when short-name (s/lower-case short-name))
            :version-id.lowercase (when version-id (s/lower-case version-id))

            :provider-id provider-id
            :provider-id.lowercase (s/lower-case provider-id)
            :granule-ur granule-ur
            :granule-ur.lowercase2 (s/lower-case granule-ur)
            :producer-gran-id producer-gran-id
            :producer-gran-id.lowercase2 (when producer-gran-id (s/lower-case producer-gran-id))
            :day-night day-night
            :day-night.lowercase (when day-night (s/lower-case day-night))
            :access-value access-value

            ;; Provides sorting on a combination of producer granule id and granule ur
            :readable-granule-name-sort2 (s/lower-case (or producer-gran-id granule-ur))

            :platform-sn platform-short-names
            :platform-sn.lowercase  (map s/lower-case platform-short-names)
            :instrument-sn instrument-short-names
            :instrument-sn.lowercase  (map s/lower-case instrument-short-names)
            :sensor-sn sensor-short-names
            :sensor-sn.lowercase  (map s/lower-case sensor-short-names)
            :project-refs project-refs
            :project-refs.lowercase (map s/lower-case project-refs)
            :size size
            :cloud-cover cloud-cover
            :orbit-calculated-spatial-domains (ocsd/ocsds->elastic-docs umm-granule)
            :attributes (attrib/psa-refs->elastic-docs parent-collection umm-granule)
            :revision-date revision-date
            :downloadable downloadable
            :browsable browsable
            :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
            :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))
            :two-d-coord-name two-d-coord-name
            :two-d-coord-name.lowercase (when two-d-coord-name (s/lower-case two-d-coord-name))
            :start-coordinate-1 start-coordinate-1
            :end-coordinate-1 end-coordinate-1
            :start-coordinate-2 start-coordinate-2
            :end-coordinate-2 end-coordinate-2
            :atom-links atom-links
            :orbit-calculated-spatial-domains-json ocsd-json}
           (spatial->elastic parent-collection umm-granule))))
