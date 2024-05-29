(ns cmr.common.concepts
  "This contains utility functions and vars related to concepts in the CMR"
  (:require
   [clojure.set :as cset]
   [clojure.string :as string]
   [cmr.common.generics :as common-generic]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.umm-spec.validation.util :as v-util]
   [inflections.core :as inf]))

(def generic-concept-types->concept-prefix
  "Gets an array of generic concept types.
   Return {:generic (str X) :grid (str GRD)...}"
  (merge {:generic "X"} (common-generic/approved-generic-concept-prefixes)))

(def generic-concept-prefix->concept-type
  "Maps a generic concept id prefix to the concept type.
   Return: {(str X) :generic (str GRD) :grid...}"
  (cset/map-invert generic-concept-types->concept-prefix))

(defn get-generic-concept-types-array
  "Gets the array of generic concept types and optionally modify those values.
   By default, no parameters will return the list as is, however if a function
   is passed in that takes one value, this value will be changed in some way.
   Expected usage is to pass in pluralize-concept-type."
  ([] (get-generic-concept-types-array identity))
  ([modifier-func]
   (reduce (fn [coll, item] (conj coll (modifier-func (key item))))
           []
           (common-generic/approved-generic-concept-prefixes))))

(def get-generic-non-draft-concept-types-array
  "Gets all of the generic concept types that are not drafts."
  (vec
   (filter #(not (string/includes? % "draft")) (get-generic-concept-types-array))))

(def get-draft-concept-types-array
  "Gets all of the generic concept types that are drafts."
  (vec
   (filter #(string/includes? % "draft") (get-generic-concept-types-array))))

(defn get-concept-type-of-draft
  "Gets the concept type of the document that is contained in the draft."
  [draft-concept-type]
  (let [draft-concept-type-name (when draft-concept-type
                                  (name draft-concept-type))
        concept-type-name (when draft-concept-type-name
                            (re-find #".+?(?=-draft)" draft-concept-type-name))]
    (when concept-type-name
      (keyword concept-type-name))))

(defn is-draft-concept?
  "Determines if the passed in concept type is a draft concept or not."
  [concept-type]
  (let [concept-type-name (when concept-type
                            (name concept-type))]
    (when concept-type-name
      (string/includes? concept-type-name "draft"))))

(defn generic-concept?
  "Return true if the passed in concept is a generic concept"
  [concept]
  (some #(= concept %) (get-generic-concept-types-array)))

(def concept-types
  "This is the set of the types of concepts in the CMR."
  (into #{:access-group
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
    :generic-association}
    (keys generic-concept-types->concept-prefix)))

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

(defn pluralize-concept-type-name
  "Pluralizes the passed in concept keyword/string and returns the string. The compliment
   function is singularize-concept-type-name"
  [concept-key]
  (inf/plural (name concept-key)))

(defn singularize-concept-type-name
  "Singularize the passed in concept keyword/string and returns the string. The compliment
   function is pluralize-concept-type-name"
  [concept-key]
  (inf/singular (name concept-key)))

(defn pluralize-concept-type
  "Pluralizes the passed in concept keyword/string and returns it. The compliment
   function is singularize-concept-type"
  [concept-key]
  (keyword (pluralize-concept-type-name concept-key)))

(defn singularize-concept-type
  "Singularize the passed in concept keyword/string and returns it. The compliment
   function is pluralize-concept-type"
  [concept-key]
  (keyword (singularize-concept-type-name concept-key)))

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
   (if (string? concept-id)
     (let [valid-prefixes (string/join "|" (keys concept-prefix->concept-type))
           regex (re-pattern (str "(" valid-prefixes ")\\d+-[A-Za-z0-9_]+"))]
       (when-not (re-matches regex concept-id)
         [(format "%s [%s] is not valid."
                  (-> param name string/capitalize)
                  (util/html-escape concept-id))]))
     [(format "%s [%s] is not valid."
              (-> param name string/capitalize)
              (util/html-escape concept-id))])))

(def validate-concept-id
  "Validates a concept-id and throws an error if invalid"
  (v-util/build-validator :bad-request concept-id-validation))

(defn concept-type-validation
  "Validates a concept type is known. Returns an error if invalid. A string or keyword can be passed
   in."
  [concept-type]
  (let [concept-type (cond
                       (string? concept-type) (keyword concept-type)
                       (keyword? concept-type) concept-type
                       :else (errors/internal-error! (format "Received invalid concept-type [%s]"
                                                             concept-type)))]
    (when-not (concept-types concept-type)
      [(format "[%s] is not a valid concept type." (name concept-type))])))

(def validate-concept-type
  "A function that will validate concept-type and thrown and exception if it's invalid"
  (v-util/build-validator :bad-request concept-type-validation))

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
      (string/split #"\d" 2)
      first))

(defn build-concept-id
  "Converts a map of concept-type sequence-number and provider-id to a concept-id"
  [{:keys [concept-type sequence-number provider-id]}]
  (let [prefix (concept-type->concept-prefix concept-type)]
    (format "%s%d-%s" prefix sequence-number provider-id)))

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
