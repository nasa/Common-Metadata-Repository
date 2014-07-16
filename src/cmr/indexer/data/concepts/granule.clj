(ns cmr.indexer.data.concepts.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cheshire.core :as json]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.umm.core :as umm]
            [cmr.umm.related-url-helper :as ru]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.data.concepts.temporal :as temporal]
            [cmr.indexer.data.concepts.attribute :as attrib]
            [cmr.indexer.data.concepts.orbit-calculated-spatial-domain :as ocsd]
            [cmr.indexer.data.concepts.spatial :as spatial]
            [cmr.common.cache :as cache])
  (:import cmr.spatial.mbr.Mbr))

(defn- fetch-parent-collection
  "Retrieve the parent collection umm from the db"
  [context parent-collection-id]
  (let [parent-collection-cache (get-in context [:system :parent-collection-cache])
        concept (mdb/get-latest-concept context parent-collection-id)]
    (assoc (umm/parse-concept concept) :concept-id parent-collection-id)))

(defn- get-parent-collection
  [context parent-collection-id]
  (if-let [cache (get-in context [:system :parent-collection-cache])]
    (cache/cache-lookup cache parent-collection-id
                        (partial fetch-parent-collection context parent-collection-id))
    (fetch-parent-collection context parent-collection-id)))


(defn spatial->elastic
  [parent-collection granule]
  (try
    (when-let [geometries (seq (get-in granule [:spatial-coverage :geometries]))]
      (let [gsr (get-in parent-collection [:spatial-coverage :granule-spatial-representation])]
        ;; TODO Add support for all granule spatial representations and geometries
        (cond
          (= gsr :geodetic)
          (spatial/spatial->elastic-docs gsr granule)

          (= gsr :cartesian)
          (let [{supported true not-supported false}
                (group-by (comp some? spatial/temporary-supported-cartesian-types type) geometries)]
            (when (seq not-supported)
              (info "Ignoring indexing spatial of spatial for non supported cartesian types: " (pr-str not-supported)))
            (spatial/spatial->elastic-docs gsr (assoc-in granule [:spatial-coverage :geometries] supported)))

          :else
          (info "Ignoring indexing spatial of granule spatial representation of" gsr))))
    (catch Throwable e
      (error e (format "Error generating spatial for granule: %s. Skipping spatial."
                       (pr-str granule))))))

(defmethod es/concept->elastic-doc :granule
  [context concept umm-granule]
  (let [{:keys [concept-id extra-fields provider-id revision-date format]} concept
        {:keys [parent-collection-id]} extra-fields
        parent-collection (get-parent-collection context parent-collection-id)
        {:keys [granule-ur data-granule temporal platform-refs project-refs related-urls cloud-cover]} umm-granule
        {:keys [size producer-gran-id day-night]} data-granule
        platform-short-names (map :short-name platform-refs)
        instrument-refs (mapcat :instrument-refs platform-refs)
        instrument-short-names (remove nil? (map :short-name instrument-refs))
        sensor-refs (mapcat :sensor-refs instrument-refs)
        sensor-short-names (remove nil? (map :short-name sensor-refs))
        start-date (temporal/start-date :granule temporal)
        end-date (temporal/end-date :granule temporal)
        atom-links (map json/generate-string (ru/atom-links related-urls))
        ;; not empty is used below to get a real true false value
        downloadable (not (empty? (ru/downloadable-urls related-urls)))
        browsable (not (empty? (ru/browse-urls related-urls)))
        update-time (get-in umm-granule [:data-provider-timestamps :update-time])
        update-time (f/unparse (f/formatters :date-time) update-time)]
    (merge {:concept-id concept-id
            :collection-concept-id parent-collection-id

            :entry-title (:entry-title parent-collection)
            :original-format format
            :update-time update-time

            :entry-title.lowercase (s/lower-case (:entry-title parent-collection))
            :short-name.lowercase (s/lower-case (get-in parent-collection [:product :short-name]))
            :version-id.lowercase (s/lower-case (get-in parent-collection [:product :version-id]))

            :provider-id provider-id
            :provider-id.lowercase (s/lower-case provider-id)
            :granule-ur granule-ur
            :granule-ur.lowercase (s/lower-case granule-ur)
            :producer-gran-id producer-gran-id
            :producer-gran-id.lowercase (when producer-gran-id (s/lower-case producer-gran-id))
            :day-night day-night
            :day-night.lowercase (when day-night (s/lower-case day-night))

            ;; Provides sorting on a combination of producer granule id and granule ur
            :readable-granule-name-sort (s/lower-case (or producer-gran-id granule-ur))

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
            :atom-links atom-links}
           (spatial->elastic parent-collection umm-granule))))
