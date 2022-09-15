(ns cmr.common.generics
  "Defines utilities for new generic document pipeline. Most functions will deal
   with either returning generic config files, or lists of approved generics."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common.config :as cfg]
   [cmr.common.log :as log :refer (error)]
   [cmr.schema-validation.json-schema :as js-validater]))

(defn approved-generic?
  "Check to see if a requested generic is on the approved list.
   Parameters:
   * schema: schema keyword like :grid
   * version: string like 0.0.1
   Returns: true if schema and version are supported, nil otherwise"
  [schema version]
  (when (and schema version)
    (some #(= version %) (schema (cfg/approved-pipeline-documents)))))

(defn lattest-approved-documents
  "Return a map of all the configured approved generics and the lattest version
   string for each one.
   Return {:doc-type \"ver.ion.string\"}"
  []
  (reduce (fn [data item]
            (assoc data (first item) (last (second item))))
          {}
          (cfg/approved-pipeline-documents)))

(defn lattest-approved-document-types
  "Return a list of configured approved generic keywords
   Returns: (:grid :dataqualitysummary ...)"
  []
  (keys (lattest-approved-documents)))

(defn read-schema-file
  "Return the specific schema given the schema keyword name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * file-name: [metadata | index | schema]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version]
  (try
    (-> "schemas/%s/v%s/%s.json"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp))
    (catch Exception e
      (error
       (format (str "The %s.json file for schema [%s] version [%s] cannot be found."
                    "Please make sure that it exists. %s")
               (name file-name)
               (name generic-keyword)
               generic-version
               (.getMessage e))))))

(defn read-schema-index
  "Return the schema index configuration file given the schema name and version
   number. Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "index" generic-keyword generic-version))

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

(defn validate-index-against-schema
  "Validate a document, returns an array of errors if there are problems
   Parameters:
   * raw-json, json as a string to validate
   Returns: list of errors or nil"
  [raw-json]
  (let [schema-file (read-schema-file :schema :index "0.0.1")
        schema-obj (js-validater/json-string->json-schema schema-file)]
    (js-validater/validate-json schema-obj raw-json)))

(defn approved-generic-concept-prefixes
  "Return the active list of approved generic content types with the defined
   prefix in the :SubConceptType field found in the index.json file. If field is
   not defined, then X is used.
   Parameters: none, based off approved-documents?
   Return: {doc-type \"concept-prefix\"}"
  []
  (reduce (fn [data item]
            (let [generic-keyword (first item)
                  index-raw (read-schema-index generic-keyword (second item))
                  parse-errors (validate-index-against-schema index-raw)]
              (when-not (some? parse-errors)
                (assoc data
                       generic-keyword
                       (get (json/parse-string index-raw true) :SubConceptType "X")))))
          {}
          (lattest-approved-documents)))
