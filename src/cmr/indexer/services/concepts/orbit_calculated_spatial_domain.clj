(ns cmr.indexer.services.concepts.orbit-calculated-spatial-domain
  "Contains functions for converting orbit calcuated spatial domains into a elastic documents"
  (:require [clojure.string :as s]
            [cmr.common.services.errors :as errors]))

(defn ocsd->elastic-doc
  "Converts a OCSD into the portion going in an elastic document"
  [ocsd]
  (try
  (let [{:keys [orbital-model-name
                orbit-number
                start-orbit-number
                stop-orbit-number
                equator-crossing-longitude
                equator-crossing-date-time]} ocsd]
    {:orbit-number orbit-number
     :start-orbit-number start-orbit-number
     :stop-orbit-number stop-orbit-number
     :equator-crossing-longitude equator-crossing-date-time
     :equator-crossing-date-time equator-crossing-date-time})
  (catch Throwable e
    (println e)
    (.printStackTrace e))))

(defn ocsds->elastic-docs
  "Converts the ocsds into a list of elastic documents"
  [granule]
  (map ocsd->elastic-doc granule))