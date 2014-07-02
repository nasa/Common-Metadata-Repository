(ns cmr.indexer.services.concepts.collection
  "Contains functions to parse and convert collection concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.indexer.services.index-service :as idx]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.umm.echo10.related-url :as ru]
            [cmr.indexer.services.concepts.temporal :as temporal]
            [cmr.indexer.services.concepts.attribute :as attrib]
            [cmr.indexer.services.concepts.science-keyword :as sk]
            [cmr.indexer.services.concepts.spatial :as spatial])
  (:import cmr.spatial.mbr.Mbr))


(defn spatial->elastic
  [collection]
  (try
    (when-let [geometries (seq (get-in collection [:spatial-coverage :geometries]))]
      (let [sr (get-in collection [:spatial-coverage :spatial-representation])]
        ;; TODO Add support for all spatial representations and geometries
        (cond
          (= sr :geodetic)
          (spatial/spatial->elastic-docs sr collection)

          (= sr :cartesian)
          (let [{supported true not-supported false} (group-by #(= (type %) Mbr) geometries)]
            (when (seq not-supported)
              (info "Ignoring indexing spatial of spatial for non supported cartesian types"))
            (spatial/spatial->elastic-docs sr (assoc-in collection [:spatial-coverage :geometries] supported)))

          :else
          (info "Ignoring indexing spatial of collection spatial representation of" sr))))
    (catch Exception e
      (error e (format "Error generating spatial for collection: %s. Skipping spatial."
                       (pr-str collection))))))

(defmethod idx/concept->elastic-doc :collection
  [context concept collection]
  (let [{:keys [concept-id provider-id revision-date]} concept
        {{:keys [short-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-id entry-title temporal related-urls]} collection
        platforms (:platforms collection)
        platform-short-names (map :short-name platforms)
        instruments (mapcat :instruments platforms)
        instrument-short-names (remove nil? (map :short-name instruments))
        sensors (mapcat :sensors instruments)
        sensor-short-names (remove nil? (map :short-name sensors))
        project-short-names (map :short-name (:projects collection))
        two-d-coord-names (map :name (:two-d-coordinate-systems collection))
        spatial-keywords (:spatial-keywords collection)
        orgs (:organizations collection)
        archive-center-val (remove nil? (for [org orgs]
                                          (let [{:keys [type org-name]} org]
                                            (when (= :archive-center type) org-name))))
        start-date (temporal/start-date :collection temporal)
        end-date (temporal/end-date :collection temporal)
        downloadable (not (empty? (filter ru/downloadable-url? related-urls)))]
    (merge {:concept-id concept-id
            :entry-id entry-id
            :entry-id.lowercase (s/lower-case entry-id)
            :entry-title entry-title
            :entry-title.lowercase (s/lower-case entry-title)
            :provider-id provider-id
            :provider-id.lowercase (s/lower-case provider-id)
            :short-name short-name
            :short-name.lowercase (s/lower-case short-name)
            :version-id version-id
            :version-id.lowercase (s/lower-case version-id)
            :revision-date revision-date
            :processing-level-id processing-level-id
            :processing-level-id.lowercase (when processing-level-id (s/lower-case processing-level-id))
            :collection-data-type collection-data-type
            :collection-data-type.lowercase (when collection-data-type (s/lower-case collection-data-type))
            :platform-sn platform-short-names
            :platform-sn.lowercase  (map s/lower-case platform-short-names)
            :instrument-sn instrument-short-names
            :instrument-sn.lowercase  (map s/lower-case instrument-short-names)
            :sensor-sn sensor-short-names
            :sensor-sn.lowercase  (map s/lower-case sensor-short-names)
            :project-sn project-short-names
            :project-sn.lowercase  (map s/lower-case project-short-names)
            :two-d-coord-name two-d-coord-names
            :two-d-coord-name.lowercase  (map s/lower-case two-d-coord-names)
            :spatial-keyword spatial-keywords
            :spatial-keyword.lowercase  (map s/lower-case spatial-keywords)
            :attributes (attrib/psas->elastic-docs collection)
            :science-keywords (sk/science-keywords->elastic-doc collection)
            :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
            :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))
            :archive-center archive-center-val
            :archive-center.lowercase (map s/lower-case archive-center-val)
            :downloadable downloadable}
           (spatial->elastic collection))))

