(ns cmr.common.concepts
  "This contains utility functions and vars related to concepts in the CMR"
  (:require [clojure.set]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]))

(def concept-types
  "This is the set of the types of concepts in the CMR."
  #{:collection :granule})

(def concept-prefix->concept-type
  "Maps a concept id prefix to the concept type"
  {"C" :collection
   "G" :granule})

(def concept-type->concept-prefix
  "Maps a concept type to the concept id prefix"
  (clojure.set/map-invert concept-prefix->concept-type))

(defn concept-id-validation
  "Validates the concept id and returns errors if it's invalid. Returns nil if valid."
  [concept-id]
  (let [regex #"[CG]\d+-[A-Za-z0-9_]+"]
    (when-not (re-matches regex concept-id)
      [(format "Concept-id [%s] is not valid." concept-id)])))

(def validate-concept-id
  "Validates a concept-id and throws an error if invalid"
  (util/build-validator :bad-request concept-id-validation))

(defn concept-type-validation
  "Validates a concept type is known. Returns an error if invalid. A string or keyword can be passed in."
  [concept-type]
  (let [concept-type (cond
                       (string? concept-type) (keyword concept-type)
                       (keyword? concept-type) concept-type
                       :else (errors/internal-error! (format "Received invalid concept-type [%s]" concept-type)))]
    (when-not (concept-types concept-type)
      [(format "[%s] is not a valid concept type." (name concept-type))])))

(def validate-concept-type
  "A function that will validate concept-type and thrown and exception if it's invalid"
  (util/build-validator :bad-request concept-type-validation))


(defn parse-concept-id
  "Split a concept id into concept-type-prefix, sequence number, and provider id."
  [concept-id]
  (validate-concept-id concept-id)
  (let [prefix (subs concept-id 0 1)
        ^String seq-num (re-find #"\d+" concept-id)
        provider-id (get (re-find #"\d+-(.*)" concept-id) 1)]
    {:concept-type (concept-prefix->concept-type prefix)
     :sequence-number (Long. seq-num)
     :provider-id provider-id}))

(defn build-concept-id
  "Converts a map of concept-type sequence-number and provider-id to a concept-id"
  [{:keys [concept-type sequence-number provider-id]}]
  (let [prefix (concept-type->concept-prefix concept-type)]
    (format "%s%d-%s" prefix sequence-number provider-id)))

(defn concept-id->type
  "Returns concept type for the given concept-id"
  [concept-id]
  (concept-prefix->concept-type (subs concept-id 0 1)))