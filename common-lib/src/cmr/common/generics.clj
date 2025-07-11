(ns cmr.common.generics
  "Defines utilities for new generic document pipeline. Most functions will deal
   with either returning generic config files, or lists of approved generics."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :as cfg]
   [cmr.common.log :as log :refer [error, debug]]
   [cmr.schema-validation.json-schema :as js-validater]
   [inflections.core :as inf]))

(defn- approved-generic*
  "Check to see if a requested generic is on the approved list.
   Parameters:
   * schema: schema keyword like :grid
   * version: string like 0.0.1
   Returns: true if schema and version are supported, nil otherwise"
  [schema version]
  (when (and schema version)
    (debug (format "Making a request for Generic [%s] at version [%s]" schema version))
    (some #(= version %) (schema (cfg/approved-pipeline-documents)))))

(def approved-generic?
  "Check to see if a requested generic is on the approved list.
   Parameters:
   * schema: schema keyword like :grid
   * version: string like 0.0.1
   Returns: true if schema and version are supported, nil otherwise"
  (memoize approved-generic*))

(defn- latest-approved-documents*
  "Return a map of all the configured approved generics and the latest version
   string for each one.
   Return {:doc-type \"1.2.3\"}"
  []
  (debug "Making a request for All Generic documents")
  (reduce (fn [data item]
            (assoc data (first item) (last (second item))))
          {}
          (cfg/approved-pipeline-documents)))

(def latest-approved-documents
  "Cached - Return a map of all the configured approved generics and the latest version
  string for each one.
  Return {:doc-type \"1.2.3\"}"
  (memoize latest-approved-documents*))

(def documents-all-versions
  "Return the list of all versions of the generic documents in the system"
  (cfg/approved-pipeline-documents))

(defn current-generic-version
  "The current/latest version of the generic UMM schema
   similar to umm-spec-lib/src/cmr/umm_spec/versioning.clj.
   Parameters:
   * generic-keyword: [:grid | ...]
   Returns: string"
  [concept-type]
  (-> documents-all-versions concept-type last))

(defn latest-approved-document-types
  "Return a list of configured approved generic keywords
   Returns: (:grid :data-quality-summary ...)"
  []
  (keys (latest-approved-documents)))

(defn- read-schema-file*
  "Return the specific schema given the schema keyword name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * file-name: [metadata | config | schema]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version]
  (debug (format "Making a request for Generic file [%s] [%s] at version [%s]"
                 file-name
                 generic-keyword
                 generic-version))
  (try
    (-> "schemas/%s/v%s/%s.json"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp))
    (catch Exception e
      (error
       (format (str "The %s.json file for schema [%s] version [%s] cannot be found. "
                    " - [%s] - "
                    "Please make sure that it exists. %s")
               (name file-name)
               (name generic-keyword)
               generic-version
               (format "schemas/%s/v%s/%s.json"
                       (name generic-keyword)
                       generic-version
                       (name file-name))
               (.getMessage e))))))

(def read-schema-file
  "Cached - Return the specific schema given the schema keyword name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * file-name: [metadata | config | schema]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  (memoize read-schema-file*))

(defn read-schema-config
  "Return the schema config file given the schema name and version
   number. Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "config" generic-keyword generic-version))

(defn read-schema-specification
  "Return the schema specification file given the schema name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "schema" generic-keyword generic-version))

(defn read-schema-example
  "Return the schema example metadata file given the schema name and version
   number. Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "metadata" generic-keyword generic-version))

(defn validate-metadata-against-schema
  "Used to validate a metadata document against a schema
   * raw-json: raw json which may or may not be correct
   * generic-schema: generic name, such as :grid
   * version: schema version to check, such the string 1.0.0
   * throw?: true if errors are to be thrown, false to return
   Returns: list of errors, nil, or throws errors"
  ([raw-json generic-schema version]
   (validate-metadata-against-schema raw-json generic-schema version false))
  ([raw-json generic-schema version throw?]
   (let [schema-file (read-schema-file :schema generic-schema version)
         schema-obj (js-validater/json-string->json-schema schema-file)]
     (js-validater/validate-json schema-obj raw-json throw?))))

(defn validate-config-against-schema
  "Validate a document, returns an array of errors if there are problems
   Parameters:
   * raw-json, json as a string to validate
   Returns: list of errors or nil"
  [raw-json]
  (validate-metadata-against-schema raw-json :config "0.0.1"))

(defn- read-generic-config*
  "Load the actual configuration file for a generic and pull out the Index name to use."
  [concept-type]
  (let [concept-ver (get (cmr.common.generics/latest-approved-documents) concept-type)
        config-raw (cmr.common.generics/read-schema-config concept-type concept-ver)
        parse-errors (validate-config-against-schema config-raw)]
    (if (some? parse-errors)
      (do
        (error (format "Error while parsing generic config file %s: %s" concept-type parse-errors))
        {:error parse-errors})
      (json/parse-string config-raw true))))

(defn read-generic-config
  "Load the Generic configuration file and return an EDN representation of the file."
  [concept-type]
  ((memoize read-generic-config*) concept-type))

(defn- approved-generic-concept-prefixes*
  "Return the active list of approved generic content types with the defined
   prefix in the :SubConceptType field found in the config.json file. If field is
   not defined, then X is used.
   Parameters: none, based off approved-documents?
   Return: {doc-type \"concept-prefix\"}"
  []
  (reduce (fn [data item]
            (let [generic-keyword (first item)
                  config-raw (read-schema-config generic-keyword (second item))
                  parse-errors (validate-config-against-schema config-raw)]
              (when-not (some? parse-errors)
                (assoc data
                       generic-keyword
                       (get (json/parse-string config-raw true) :SubConceptType "X")))))
          {}
          (latest-approved-documents)))

(def approved-generic-concept-prefixes
  "Cached - Return the active list of approved generic content types with the defined
   prefix in the :SubConceptType field found in the config.json file. If field is
   not defined, then X is used.
   Parameters: none, based off approved-documents?
   Return: {doc-type \"concept-prefix\"}"
  (memoize approved-generic-concept-prefixes*))

(def generic-concept-types-reg-ex
  "Creates a regular expression for all of the generic concepts. Used to create API endpoints."
  (->> (latest-approved-document-types)
       (map name)
       (string/join "|")))

(def plural-generic-concept-types-reg-ex
  "Creates a pluralized regular expression for all of the generic concepts. Used to create API
   endpoints."
  (->> (latest-approved-document-types)
       (map inf/plural)
       (string/join "|")))

(defn jq->list
  "To make configuration authoring simple for humans, fields of a JSON record are
   to be denoted using a syntax simaler to the jq unix command. This syntax will
   define a path into a nested set of fields. The jq path is passed in to this
   function and will be converted to a list that can be used with the built in
   clojure function get-in to retrive JSON field content.
   Example: .Level1.Level2[1].Level3 -> [:Level1 :Level2 1 :Level3]"
  ([jq-path] (jq->list jq-path str))
  ([jq-path namer]
   (into [] (map (fn
                   [value]
                   (if (every? #(Character/isDigit %) value)
                     (Integer/parseInt value)
                     (namer value)))
                 (-> jq-path
                     (string/replace #"^\." "")
                     (string/replace #"\[(\d+)\]" ".$1")
                     (string/split #"\."))))))
