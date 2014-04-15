(ns cmr.common.concepts
  "This contains utility functions and vars related to concepts in the CMR"
  (:require [clojure.set]
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

(defn validate-concept-id
  "Validates a concept-id and throws an error if invalid"
  [concept-id]
  (when-let [[error-msg] (concept-id-validation concept-id)]
    (errors/throw-service-error :bad-request error-msg)))

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
