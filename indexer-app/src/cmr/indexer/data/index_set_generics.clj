(ns cmr.indexer.data.index-set-generics
  (:refer-clojure :exclude [update])
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as cs]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.index-util :as m :refer [defmapping defnestedmapping]]
   [cmr.indexer.data.concepts.generic-util :as gen-util]
   [cmr.indexer.data.index-set-elasticsearch :as index-set-es]
   [cmr.schema-validation.json-schema :as js-validater]
   [cmr.transmit.metadata-db :as meta-db]))

;; TODO: Generic work - move this to a location where index and ingest can find it
(defn- validate-index-against-schema
  "validate a document, returns an array of errors if there are problems
   Parameters:
   * raw-json, json as a string to validate"
  [raw-json]  
  (let [schema-file (slurp (clojure.java.io/resource "schemas/index/v0.0.1/schema.json"))
        schema-obj (js-validater/json-string->json-schema schema-file)]
    (js-validater/validate-json schema-obj raw-json)))

;; TODO: Generic work - move this to the config file in future releases
(defconfig elastic-generic-index-num-shards
  "Number of shards to use for the generic document index"
  {:default 5 :type Long})

;; TODO: Generic work - move this to the config file in future releases
(def generic-setting {:index
                      {:number_of_shards (elastic-generic-index-num-shards)
                       :number_of_replicas 1,
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
   :revision-date m/date-field-mapping})

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

;; TODO: Generic work: We need to check throws here. When something is wrong in the schema,
;; 500 errors are thrown.
(defn generic-mappings-generator
  "create a map with an index for each of the known generic types. This is used
   to inform Elastic on CMR boot on what an index should look like
   Return looks like this:
   {:generic-grid
    {:indexes []
     :mapping {:properties {:index-key-name {:type 'type'}}}}}
   "
  []
  (reduce (fn [data gen-name]
            (let [gen-ver (last (gen-name (cfg/approved-pipeline-documents)))
                  index-definition-str (-> "schemas/%s/v%s/index.json"
                                       (format (name gen-name) gen-ver)
                                       (clojure.java.io/resource)
                                       (slurp))
                  ;; TODO: Generic work - need to fix or change the validation - are we supposed to validate the
                  ;; index.json file?
                  index-definition  ;(when-not (validate-index-against-schema index-definition-str)
                                      (json/parse-string index-definition-str true)
                  index-list (gen-util/only-elastic-preferences (:Indexes index-definition))]
              (if index-definition
                (assoc data
                       (keyword (str "generic-" (name gen-name)))
                       {:indexes [{:name (format "generic-%s" (name gen-name))
                                   :settings generic-setting}
                                  {:name (format "all-generic-%s-revisions" (name gen-name))
                                   :settings generic-setting}]
                        :mapping {:properties (reduce mapping->index-key base-indexes index-list)}}
                       )
                (do
                  (error (format "Could not parse schema %s version %s." (name gen-name) gen-ver))
                  data))))
          {}
          (keys (cfg/approved-pipeline-documents))))
