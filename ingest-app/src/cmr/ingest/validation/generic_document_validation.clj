(ns cmr.ingest.validation.generic-document-validation
  "Provides functions to validate the ingest generic document"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.generics :as generics]
   [cmr.common.log :refer (info error)]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.metadata-db :as mdb]))

(def schema-validation-cache-key
  "The cache key for the schema validation functions cache."
  :schema-validation-functions)

(def SCHEMA_CACHE_TIME
  "The number of milliseconds schema validation functions will be cached."
  (* 60 60 1000)) ;; 1 hour

(defn create-schema-validation-cache
  "Creates a cache for schema validation functions."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl SCHEMA_CACHE_TIME}))

(defn- fetch-existing-concepts
  "Fetch existing concepts of the given type from metadata-db for uniqueness validation"
  [context concept-type]
  (try
    (mdb/find-concepts context {:latest true} concept-type)
    (catch Exception e
      (error (str "Error fetching concepts for generic validations: " (.getMessage e)))
      []))) 

(defn- validate-uniqueness
  "Validates that the combination of field values is unique in the collection.
   Returns a sequence of error messages if validation fails, empty sequence otherwise."
  [context concept fields]
  (let [concept-type (:concept-type concept)]
    (if-let [existing-concepts (fetch-existing-concepts context concept-type)]
      (let [;; Helper function to extract field values from a concept
            get-field-values (fn [c fs]
                               (let [metadata (json/parse-string (:metadata c) true)]
                                 (mapv #(let [field-path (rest (string/split % #"\."))
                                              keyword-path (keyword (first field-path))]
                                          (get-in metadata[keyword-path]))
                                       fs)))

            ;; Extract values for the specified fields from the current concept
            field-values (get-field-values concept fields)

            ;; Check if any other document in the collection has the same combination of values
            duplicate-concepts (filter (fn [existing-concept]
                                         ;; Debug print to show values being compared
                                         (and (not= (:native-id existing-concept) (:native-id concept))
                                              (not= (:deleted existing-concept) true)
                                              (= (set (get-field-values existing-concept fields))
                                                 (set field-values))))
                                       existing-concepts)]
        (if (seq duplicate-concepts)
          (let [duplicate-concept-ids (map :concept-id duplicate-concepts)
                field-names (mapv #(string/join "." (rest (string/split % #"\."))) fields)
                display-values (mapv str field-values)]
            (info "Duplicate concept IDs found: " duplicate-concept-ids)
            [(format "Values %s for fields %s must be unique for concept type %s. Duplicate concept IDs: %s"
                     (string/join ", " display-values)
                     (string/join ", " field-names)
                     (str concept-type)
                     (string/join ", " duplicate-concept-ids))])
          []))
      [])))

(defn- validate-by-type
  "Validates fields based on validation type.
   Returns a sequence of error messages if validation fails, empty sequence otherwise."
  [context concept validation-type fields validation-value]
  (case validation-type
    "unique" (validate-uniqueness context concept fields)
    ;; Default case
    [(str "Unknown validation type: " validation-type)]))

(defn- validate-with-schema
  "Validates a concept against the schema validations.
   Returns a sequence of error messages if validation fails, empty sequence otherwise."
  [context concept schema]
  (let [validations (:Validations schema)]
    (if validations
      (mapcat (fn [validation]
                (let [validation-type (:ValidationType validation)
                      fields (:Fields validation)
                      validation-value (:ValidationValue validation)]
                  (validate-by-type context concept validation-type fields validation-value)))
              validations)
      [])))

(defn- load-schema-validation
  "Loads a single schema validation function for a concept type and version"
  [concept-type version]
  (try
    (if (generics/approved-generic? concept-type version)
      (let [schema-json (generics/read-schema-config concept-type version)
            schema (json/parse-string schema-json true)]
        (fn [context concept]
          (validate-with-schema context concept schema)))
      (do
        (error "Schema version not approved for" concept-type "version" version)
        nil))
    (catch Exception e
      (error "Error loading schema for" concept-type "version" version ":" (.getMessage e))
      nil)))

(defn- extract-concept-metadata-spec
  "Extract metadata specification info from concept"
  [concept]
  (let [parsed-data (json/parse-string (:metadata concept))]
    (when parsed-data
      {:name (get-in parsed-data [:MetadataSpecification :Name])
       :version (get-in parsed-data [:MetadataSpecification :Version])})))

(defn- load-schema-validators
  "Loads all schema validators for all generic concept types"
  []
  (info "Loading schema validation functions for all generic concept types")
  (let [generic-types (concepts/get-generic-concept-types-array)
        validators (reduce (fn [validators concept-type]
                             (let [current-version (generics/current-generic-version concept-type)
                                   validator (load-schema-validation concept-type current-version)]
                               (if validator
                                 (assoc validators [concept-type current-version] validator)
                                 validators)))
                           {}
                           generic-types)]
    (info "Loaded" (count validators) "schema validators")
    validators))

(defn- get-validation-functions
  "Gets the validation functions from cache if available, otherwise loads them"
  [context]
  (if-let [cache (cache/context->cache context schema-validation-cache-key)]
    ;; Return cached validation functions if available
    (cache/get-value cache :validators load-schema-validators)
    ;; No cache available, load directly
    (load-schema-validators)))

(defn validate-concept
  "Validates the given concept dynamically based on concept type.
   Throws a :bad-request service error if validation fails."
  [context concept]
  (let [concept-type (:concept-type concept)
        metadata-spec (extract-concept-metadata-spec concept)
        version (or (:version metadata-spec)
                    (generics/current-generic-version concept-type))

        ;; Get validation functions
        validators (get-validation-functions context)
        validator-fn (get validators [concept-type version])

        ;; Only run validation if a validator is defined
        errors (when validator-fn
                 (validator-fn context concept))]
    (info "Validating concept" concept "with schema version" version)

    ;; Throw service errors if any validation errors are found
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors))))