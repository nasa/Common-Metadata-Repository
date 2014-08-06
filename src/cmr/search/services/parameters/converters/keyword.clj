(ns cmr.search.services.parameters.converters.keyword
  "Contains functions for converting keyword query parameters to conditions"
  (:require [clojure.string :as str]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameters.conversion :as p]
            [cmr.common.util :as util]))

(def short-name-long-name-boost
  "The boost to apply to the short-name/long-name component of the keyword matching"
  1.4)

(def project-boost
  "The boost to apply to the campaign short name / long name fields"
  1.3)

(def platform-boost
  "The boost to apply to the platform short name / long name"
  1.3)

(def instrument-boost
  "The boost to apply to the instrument short name / long name"
  1.2)

(def sensor-boost
  "The boost to apply to the sensor short name / long name"
  1.2)

(def spatial-keyword-boost
  "The boost to apply to the spatial keyword"
  1.1)

(def science-keywords-boost
  "The boost to apply to the science keyword field"
  1.2)

;; Converts keyword parameters and values into conditions
(defmethod p/parameter->condition :keyword
  [concept-type param value options]
  (let [pattern (p/pattern-field? param options)
        keywords (str/join " " (util/prepare-keyword-field value))]
    (qm/text-condition :keyword keywords)))