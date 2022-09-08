(ns cmr.common.concepts
  "This contains utility functions and vars related to concepts in the CMR"
  (:require [clojure.set :as cset]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]))

(def generic-concept-prefix->concept-type
  "Maps a generic concept id prefix to the concept type."
  {"X" :generic
   ;; TODO: Generic work: Can I read a non common config file to read in the generics instead of coding them? This is primarily used for validation.
   "DQS" :dataqualitysummary
   "OO" :orderoption
   "SO" :serviceoption
   "SE" :serviceentry
   "GRD" :grid})

(def generic-concept-types->concept-prefix
  "Gets an array of generic concept types."
  (cset/map-invert generic-concept-prefix->concept-type))

(defn get-generic-concept-types-array
  "Gets the array of generic concept types."
  []
  (vec (keys generic-concept-types->concept-prefix)))

(defn generic-concept?
  "Return true if the passed in concept is a generic concept"
  [concept]
  (some #(= concept %) (get-generic-concept-types-array)))

(def concept-types
  "This is the set of the types of concepts in the CMR."
  #{:access-group
    :acl
    :collection
    :granule
    :humanizer
    :service
    :service-association
    :tool
    :tool-association
    :tag
    :tag-association
    :variable
    :variable-association
    :subscription
    :generic
    :dataqualitysummary
    :orderoption
    :serviceoption
    :serviceentry
    :grid
    :generic-association})

(def concept-prefix->concept-type
  "Maps a concept id prefix to the concept type"
  (merge
   {"ACL" :acl
    "AG" :access-group
    "C" :collection
    "G" :granule
    "H" :humanizer
    "S" :service
    "SA" :service-association
    "TL" :tool
    "TLA" :tool-association
    "T" :tag
    "TA" :tag-association
    "V" :variable
    "VA" :variable-association
    "SUB" :subscription
    "GA" :generic-association}
   generic-concept-prefix->concept-type))

(def concept-type->concept-prefix
  "Maps a concept type to the concept id prefix"
  (cset/map-invert concept-prefix->concept-type))
 
(def humanizer-native-id
  "The native id of the system level humanizer. There can only be one humanizer in CMR.
  We use just humanizer native id to enforce it."
  "humanizer")

(defn concept-id-validation
  "Validates both concept-id and collection-concept-id
   and returns errors if it's invalid. Returns nil if valid."
  ([concept-id]
   ;;validates concept-id
   ;;use :collection-concept-id in place of param when validating collection-concept-id
   (concept-id-validation :concept-id concept-id))
  ([param concept-id]
   (let [valid-prefixes (str/join "|" (keys concept-prefix->concept-type))
         regex (re-pattern (str "(" valid-prefixes ")\\d+-[A-Za-z0-9_]+"))]
     (when-not (re-matches regex concept-id)
       [(format "%s [%s] is not valid." (-> param name str/capitalize) concept-id)]))))

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
  (let [matcher (re-matcher #"([A-Z]+)(\d+)-(.+)" concept-id)
        [_ prefix seq-num provider-id] (re-find matcher)]
    {:concept-type (concept-prefix->concept-type prefix)
     :sequence-number (Long. ^String seq-num)
     :provider-id provider-id}))

(defn- concept-id->concept-prefix
  "Returns the concept prefix (C,G, AG, etc.) for a given concept-id."
  [concept-id]
  (-> concept-id
      (str/split #"\d" 2)
      first))

(defn build-concept-id
  "Converts a map of concept-type sequence-number and provider-id to a concept-id"
  [{:keys [concept-type sequence-number provider-id]}]
  (let [prefix (concept-type->concept-prefix concept-type)]
    (format "%s%d-%s" prefix sequence-number provider-id)))

(defn build-generic-concept-id
  "Converts a map of concept-type sequence-number and provider-id to a concept-id for generic concept types."
  [{:keys [concept-type sequence-number provider-id]}]
  (format "%s%d-%s" concept-type sequence-number provider-id))

(defn concept-id->type
  "Returns concept type for the given concept-id"
  [concept-id]
  (-> concept-id
      concept-id->concept-prefix
      concept-prefix->concept-type))

(defn concept-id->provider-id
  "Returns the provider-id associated with the given concept-id."
  [concept-id]
  (:provider-id (parse-concept-id concept-id)))
