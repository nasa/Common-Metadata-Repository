(ns cmr.indexer.services.concepts.collection
  "Contains functions to parse and convert collection concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.indexer.services.index-service :as idx]
            [cmr.umm.echo10.collection :as collection]
            [cmr.indexer.services.concepts.temporal :as temporal]
            [cmr.indexer.services.concepts.attribute :as attrib]))

(defmethod idx/parse-concept :collection
  [concept]
  (collection/parse-collection (:metadata concept)))

(defmethod idx/concept->elastic-doc :collection
  [context concept umm-concept]
  (let [{:keys [concept-id provider-id revision-date]} concept
        {{:keys [short-name version-id]} :product
         entry-id :entry-id
         entry-title :entry-title
         temporal :temporal} umm-concept
        platform-short-names (map :short-name (:platforms umm-concept))
        project-short-names (map :short-name (:projects umm-concept))
        two-d-coord-names (map :name (:two-d-coordinate-systems umm-concept))
        orgs (:organizations umm-concept)
        archive-center-val (remove nil? (for [org orgs]
                                          (let [{:keys [type org-name]} org]
                                            (when (= :archive-center type) org-name))))
        start-date (temporal/start-date :collection temporal)
        end-date (temporal/end-date :collection temporal)]
    {:concept-id concept-id
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
     :platform-sn platform-short-names
     :platform-sn.lowercase  (map s/lower-case platform-short-names)
     :project-sn project-short-names
     :project-sn.lowercase  (map s/lower-case project-short-names)
     :two-d-coord-name two-d-coord-names
     :two-d-coord-name.lowercase  (map s/lower-case two-d-coord-names)
     :attributes (attrib/psas->elastic-docs umm-concept)
     :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
     :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))
     :archive-center archive-center-val
     :archive-center.lowercase (map s/lower-case archive-center-val)}))

