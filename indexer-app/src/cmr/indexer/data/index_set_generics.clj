(ns cmr.indexer.data.index-set-generics
  (:refer-clojure :exclude [update])
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :as log :refer (error)]
   [cmr.elastic-utils.index-util :as m]
   [cmr.indexer.data.concepts.generic-util :as gen-util]
   [cmr.schema-validation.json-schema :as js-validater]))

(defn- validate-index-against-schema
  "Validate a document, returns an array of errors if there are problems
   Parameters:
   * raw-json, json as a string to validate"
  [raw-json]
  (let [schema-file (slurp (io/resource "schemas/index/v0.0.1/schema.json"))
        schema-obj (js-validater/json-string->json-schema schema-file)]
    (js-validater/validate-json schema-obj raw-json)))

(def default-generic-index-num-shards
  "This is the default generic index number of shards."
  5)

(defconfig elastic-generic-index-num-shards
  "Number of shards to use for the generic document index. This value can be overriden
  by an environment variable. This value can also be overriden in the schema specific
  configuration files. These files are found in the schemas project.
  Here is an example:
  \"IndexSetup\" : {
    \"index\" : {\"number_of_shards\" : 5,
                 \"number_of_replicas\" : 1,
                 \"refresh_interval\" : \"1s\"}
  },"
  {:default default-generic-index-num-shards :type Long})

(def generic-setting
  "This def is here as a default just in case these values are not specified in the
  schema specific configuration file found in the schemas project.
  These values can be overriden in the schema specific configuration file.
  Here is an example:
  \"IndexSetup\" : {
    \"index\" : {\"number_of_shards\" : 5,
                 \"number_of_replicas\" : 1,
                 \"refresh_interval\" : \"1s\"}
  },"
  {:index
   {:number_of_shards (elastic-generic-index-num-shards)
    :number_of_replicas 1
    :refresh_interval "1s"}})

;; By default, these are the indexes that all generics will have, these are mostly
;; from the database table
(def base-indexes
  {:concept-id m/string-field-mapping
   :revision-id m/int-field-mapping
   :deleted m/bool-field-mapping
   :gen-name m/string-field-mapping
   :gen-name-lowercase m/string-field-mapping
   :gen-version m/string-field-mapping
   :generic-type m/string-field-mapping
   :provider-id m/string-field-mapping
   :provider-id-lowercase m/string-field-mapping
   :keyword m/string-field-mapping
   :user-id m/string-field-mapping
   :revision-date m/date-field-mapping
   :native-id m/string-field-mapping
   :native-id-lowercase m/string-field-mapping
   :associations-gzip-b64 m/binary-field-mapping})

;; These are the types which are allowed to be expressed in the Index config file
(def config->index-mappings
  {"string" m/string-field-mapping
   "int" m/int-field-mapping
   "date" m/date-field-mapping})

(defn mapping->index-key
  "takes an index definition map and adds index names to the configuration
   * destination is the document to assoc to
   * index-definition contains one index config, :Names will be added using :Mapping values
   Example:
   {:Name 'add-me' :Mapping 'string'}
   Will create:
   {:add-me {:type 'keyword'}, :add-me-lowercase {:type 'keyword'}}
   "
  [destination index-definition]
  (let [index-name (string/lower-case (:Name index-definition))
        index-name-lower (str index-name "-lowercase")
        converted-mapping (get config->index-mappings (:Mapping index-definition))]
    (-> destination
        (assoc (keyword index-name) converted-mapping)
        (assoc (keyword index-name-lower) converted-mapping))))

 (defn get-settings
   "Get the elastic settings from the configuration files. If the default number of shards has
    changed, then use that instead of what was configured."
   [index-definition]
   (if-let [settings (:IndexSetup index-definition)]
     (let [config-shards (get-in settings [:index :number_of_shards])
           ;; A changed environment variable takes precedence, then the config file, then the default.
           ;; If the the environment variable = default value then the environment variable is not set.
           ;; If the environment variable is set, then use it.
           num-shards (cond
                        (not= (elastic-generic-index-num-shards) default-generic-index-num-shards)
                        (elastic-generic-index-num-shards)

                        config-shards
                        (get-in settings [:index :number_of_shards])

                        :else
                        default-generic-index-num-shards)]
       (if (= config-shards num-shards)
         settings
         (assoc-in settings [:index :number_of_shards] num-shards)))
     generic-setting))

(defn read-schema-definition
  "Read in the specific schema given the schema name and version number.  Throw an error
   if the file can't be read."
  [gen-name gen-version]
  (try
    (-> "schemas/%s/v%s/index.json"
        (format (name gen-name) gen-version)
        (io/resource)
        (slurp))
    (catch Exception e
      (error
       (format (str "The index.json file for schema [%s] version [%s] cannot be found. Please make sure that it exists."
                    (.getMessage e))
                gen-name
                gen-version)))))

(defn- validate-index-against-schema-safe
  "Wrap the validate-index-against-schema function in a try catch block to prevent
   basic JSON parse errors from throwing errors. If problems are encountered, log
   the event and return nil allowing other code to proceide with working schemas.
   "
  [raw-json schema]
  (try
    (validate-index-against-schema raw-json)
    (catch java.lang.NullPointerException npe
      (let [msg (format "%s : Null Pointer Exception while trying to validate index.json for %s."
                        (.getMessage npe)
                        schema)]
        (error msg)
        [msg]))
    (catch com.fasterxml.jackson.core.JacksonException je
      (do (error (.getMessage je))
          [(.getMessage je)]))))

(defn- json-parse-string-safe
  "Wrap the json parse-string function in a try/catch block and write an error log
   and return nil if there is a problem, otherwise return the parsed JSON. All
   parameteres are passed to json/parse-string."
  [& options]
  (try
    (apply json/parse-string options)
    (catch com.fasterxml.jackson.core.JacksonException je
      (do
       (error (.getMessage je))
        [(.getMessage je)]))
    ))

(defn generic-mappings-generator
  "create a map with an index for each of the known generic types. This is used
   to inform Elastic on CMR boot on what an index should look like
   Return looks like this:
   {:generic-grid
    {:indexes []
     :mapping {:properties {:index-key-name {:type 'type'}}}}}
   If the matching index.json file is not valid, then that schema will be logged and skipped.
   "
  []
  (reduce (fn [data gen-keyword]
            (let [gen-name (name gen-keyword)
                  gen-ver (last (gen-keyword (cfg/approved-pipeline-documents)))
                  index-definition-str (read-schema-definition gen-keyword gen-ver)
                  index-definition (when-not (validate-index-against-schema-safe
                                              index-definition-str
                                              gen-name)
                                    (json-parse-string-safe index-definition-str true))
                  index-list (gen-util/only-elastic-preferences (:Indexes index-definition))
                  generic-settings (get-settings index-definition)]
              (if index-definition
                (assoc data
                       (keyword (str "generic-" gen-name))
                       {:indexes [{:name (format "generic-%s" gen-name)
                                   :settings generic-settings}
                                  {:name (format "all-generic-%s-revisions" gen-name)
                                   :settings generic-settings}]
                        :mapping {:properties (reduce mapping->index-key base-indexes index-list)}})
                (do
                  (error (format "Could not parse the index.json file for %s version %s."
                                 gen-name
                                 gen-ver))
                  data))))
          {}
          (keys (cfg/approved-pipeline-documents))))

(comment
  ;; Since the change to distribute schemas files as a JAR makes it hard to test
  ;; a bad index.json, add the following line after the index-definition-str
  ;; declare in the let and run the function below

  ;index-definition-str (if (= :grid gen-keyword) "{bad-json}" index-definition-str)

  (when (nil? (:generic-grid (generic-mappings-generator)))
    (println "Missing Generic Grid definition"))

  )
