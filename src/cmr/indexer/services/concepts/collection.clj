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
  (let [{:keys [concept-id provider-id]} concept
        {{:keys [short-name version-id]} :product
         entry-title :entry-title
         temporal :temporal} umm-concept
        project-short-names (map :short-name (:projects umm-concept))
        two-d-coord-names (map :name (:two-d-coordinate-systems umm-concept))
        start-date (temporal/start-date :collection temporal)
        end-date (temporal/end-date :collection temporal)]
    {:concept-id concept-id
     :entry-title entry-title
     :entry-title.lowercase (s/lower-case entry-title)
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :short-name short-name
     :short-name.lowercase (s/lower-case short-name)
     :version-id version-id
     :version-id.lowercase (s/lower-case version-id)
     :project-sn project-short-names
     :project-sn.lowercase  (map s/lower-case project-short-names)
     :two-d-coord-name two-d-coord-names
     :two-d-coord-name.lowercase  (map s/lower-case two-d-coord-names)
     :attributes (attrib/psas->elastic-docs umm-concept)
     :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
     :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))}))
