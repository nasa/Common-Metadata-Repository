(ns cmr.search.services.parameters.converters.keyword
  "Contains functions for converting keyword query parameters to conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.parameters.conversion :as p]
            [cmr.indexer.data.concepts.keyword :as k]))

(def entry-tile-short-name-boost
  "The boost to apply to the entry-title/short-name component of the keyword matching"
  1.3)

(def campaign-boost
  "The boost to apply to the campaign short name  / long name fields"
  1.2)

(def platform-boost
  "The boost to apply to the platform short name / long name"
  1.2)

(def instrument-boost
  "The boost to apply to the instrument short name / long name"
  1.2)

(def sensor-boost
  "The boost to apply to the sensor short name / long name"
  1.2)

(def science-keyword-boost
  "The boost to apply to the sciece keyword field"
  1.1)

(def summary-boost
  "The boost to apply to the summary component of the keyword matching"
  0.91)

;; Converts keyword parameters and values into conditions
(defmethod p/parameter->condition :keyword
  [concept-type param value options]
  (let [pattern (p/pattern-field? param options)
        keywords (k/prepare-keyword-field value)]
    #_(qm/or-conds
      [(qm/boosted-condition
         (qm/or-conds
           [(qm/and-conds
              (map #(qm/string-condition :entry-title-keyword  % true pattern)
                   keywords))
            (qm/or-conds
              (map #(qm/string-condition :short-name % false pattern)
                   keywords))])
         entry-tile-short-name-boost)
       (qm/boosted-condition
         (qm/and-conds
           (map #(qm/string-condition :summary-keyword % true pattern)
                keywords))
         summary-boost)])))