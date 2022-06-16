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
   [cmr.indexer.data.index-set-elasticsearch :as index-set-es]
   [cmr.ingest.api.generic-documents :as ingest-generic]
   [cmr.schema-validation.json-schema :as js-validater]
   [cmr.transmit.metadata-db :as meta-db]))


(defn tee
  "a debug tool, send input to logs but don't change anything"
  [anything]
  (println anything)
  anything)

(defn- validate-index-against-schema
  "validate a document, returns an array of errors if there are problems
   Parameters:
   * raw-json, json as a string to validate"
  [raw-json]
  
  (let [schema-file (slurp (clojure.java.io/resource "schemas/index/v0.0.1/schema.json"))
        schema-obj (js-validater/json-string->json-schema schema-file)]
    (js-validater/validate-json schema-obj raw-json)))

;; use from cmr.indexer.data.concepts.generic ????
(defn- only-elastic-preferences
  "Go through all the index configurations and return only the ones related to 
   generating elastic values. If an index does not specify what type it is for,
   then assume elastic"
  [list-of-indexs]
  (keep #(if (not (nil? %)) %)
        (map
         (fn [x] (when (or (nil? (:Type x)) (= "elastic" (:Type x))) x))
         list-of-indexs)))

(defconfig elastic-generic-index-num-shards
  "Number of shards to use for the generic document index"
  {:default 5 :type Long})

(def generic-setting {:index
                      {:number_of_shards (elastic-generic-index-num-shards)
                       :number_of_replicas 1,
                       :refresh_interval "1s"}})

(def base-indexs
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

(def config->index-mappings
  {"string" m/string-field-mapping
   "int" m/int-field-mapping
   "date" m/date-field-mapping})

(defn mapping->index-key
  ""
  [source index-definition]
  (let [index-name (string/lower-case (:Name index-definition))
        index-name-lower (str index-name "-lowercase")
        converted-mapping (get config->index-mappings (:Mapping index-definition))]
    (-> source
        (assoc (keyword index-name) converted-mapping)
        (assoc (keyword index-name-lower) converted-mapping))))

(defn generic-mappings-generator-old
  "create macros for each of the known generic types"
  []
  (for [gen-name (keys ingest-generic/approved-generics)]
    (let [gen-ver (last (gen-name ingest-generic/approved-generics))
          index-definition (-> "schemas/%s/v%s/index.json"
                               (format (name gen-name) gen-ver)
                               clojure.java.io/resource
                               slurp
                               (json/parse-string true))
          indexes (:Indexes index-definition)
          gen-index-name (symbol (str gen-name "-index"))
          gen-index (reduce mapping->index-key base-indexs indexes)]
      (print gen-index)

      ;(def (symbol gen-index-name) {:dynamic "strict"
      ;       :_source {:enabled true}
      ;       :properties gen-indexes})
      ;(defmapping gen-index-name (keyword gen-index-name) "a generated mapping" gen-index)
      )))

;:grid-index {:indexs
  ;             [{:name "grid"
  ;               :settings generic-setting}
  ;            ;; This index contains all the revisions (including toomstones) and
  ;            ;; is used for all revisions searches.
  ;              {:name "all-grid-revesions"
  ;               :settings generic-setting}]
  ;             :mapping generic-mapping}



(defn generic-mappings-generator
  "create a map with an index for each of the known generic types"
  []
  (reduce (fn [data gen-name]
            (let [gen-ver (last (gen-name ingest-generic/approved-generics))
                  index-definition-str (-> "schemas/%s/v%s/index.json"
                                       (format (name gen-name) gen-ver)
                                       (clojure.java.io/resource)
                                       (slurp))
                  index-definition  (when-not (validate-index-against-schema index-definition-str)
                                      (json/parse-string index-definition-str true))]
              (if index-definition
                (assoc data
                       (keyword (str "generic-" (name gen-name)))
                       {:indexes [{:name (format "generic-%s" (name gen-name))
                                   :settings generic-setting}
                                  {:name (format "all-generic-%s-revisions" (name gen-name))
                                   :settings generic-setting}]
                        :mapping {:properties
                                  (reduce mapping->index-key base-indexs
                                          (:Indexes index-definition))}}
                       )
                (do
                  (error (format "Could not parse schema %s version %s." (name gen-name) gen-ver))
                  data))))
          {}
          (keys ingest-generic/approved-generics)))

(comment
  (generic-mappings-generator)
  (def generic-mapping-map (generic-mappings-generator))
  )

(comment defmapping generic-mapping :generic
         generic-mapping-map)
