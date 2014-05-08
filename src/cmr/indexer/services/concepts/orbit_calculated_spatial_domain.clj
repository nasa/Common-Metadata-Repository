(ns cmr.indexer.services.concepts.orbit-calculated-spatial-domain
  "Contains functions for converting orbit calcuated spatial domains into a elastic documents"
  (:require [clojure.string :as s]
            [cmr.indexer.services.concepts.attribute :as attr]
            [cmr.common.services.errors :as errors]))

(defn ocsd->elastic-doc
  "Converts a OCSD into the portion going in an elastic document"
  [ocsd]
  (let [{:keys [orbital-model-name
                orbit-number
                start-orbit-number
                stop-orbit-number
                equator-crossing-longitude
                equator-crossing-date-time]} ocsd
        ecdt (when equator-crossing-date-time (attr/value->elastic-value :datetime equator-crossing-date-time))]
    {:orbit-number orbit-number
     :start-orbit-number start-orbit-number
     :stop-orbit-number stop-orbit-number
     :equator-crossing-longitude equator-crossing-longitude
     :equator-crossing-date-time ecdt}))

(defn ocsds->elastic-docs
  "Converts the ocsds into a list of elastic documents"
  [granule]
  (map ocsd->elastic-doc (:orbit-calculated-spatial-domains granule)))