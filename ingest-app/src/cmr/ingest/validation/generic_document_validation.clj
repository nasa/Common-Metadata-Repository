(ns cmr.ingest.validation.generic-document-validation
  "Provides functions to validate the ingest generic document"
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.generics :as generics]
   [cmr.common.log :refer (info error)]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.search :as search]))

(def schema-validation-cache-key
  "The cache key for the schema validation functions cache."
  :schema-validation-functions)

(def SCHEMA_CACHE_TIME
  "The number of milliseconds schema validation functions will be cached."
  (* 24 60 60 1000)) ;; 24 hours

(defn create-schema-validation-cache
  "Creates a cache for schema validation functions."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl SCHEMA_CACHE_TIME}))

(defn- make-params-from-uniqueness-fields
  "Creates search parameters from the uniqueness fields defined in the schema.
   Converts JQ-style field paths to lowercase, hyphenated keywords for search parameters."
  [metadata fields]
  (reduce (fn [params field-path]
            (let [field-parts (generics/jq->list field-path keyword)
                  field-value (get-in metadata field-parts)
                  ;; Get the last part of the path and convert to parameter name format
                  ;; TODO make search parameters part of the config.json for a concept instead of being hardcoded.
                  param-name (-> field-parts
                                 last
                                 csk/->kebab-case
                                 keyword)]
              (if field-value
                (assoc params param-name field-value)
                params)))
          {}
          fields))

(defn- validate-uniqueness
  "Validates that the combination of field values is unique in the collection.
   The fields need to also be valid search parameters.
   Returns a sequence of error messages if validation fails, empty sequence otherwise."
  [context concept fields]
  (let [concept-type (:concept-type concept)
        native-id (:native-id concept)
        metadata (json/parse-string (:metadata concept) true)
        params (make-params-from-uniqueness-fields metadata fields)
        search-result (search/search-for-generic-concepts context concept-type params)]
    (if (< 0 (:hits search-result))
      (let [existing-concepts (:items search-result)
            duplicates (filter #(and (not= (get-in % [:meta :native-id]) native-id)
                                     (not (get-in % [:meta :deleted])))
                               existing-concepts)]
        (if (seq duplicates)
          (let [duplicate-concept-id (map #(get-in % [:meta :concept-id]) duplicates)
                field-values (mapv #(get-in metadata (generics/jq->list % keyword)) fields)
                field-names (mapv #(last (generics/jq->list % name)) fields)]
            (info "Duplicate concept ID found: " duplicate-concept-id)
            [(format "Values %s for fields %s must be unique for concept type %s. Duplicate concept ID: %s"
                     (string/join ", " field-values)
                     (string/join ", " field-names)
                     (name concept-type)
                     (string/join ", " duplicate-concept-id))])
          []))
      [])))

(defn- validate-by-type
  "Validates fields based on validation type. Any new validation added to the config.json
   should be added here and implemented in the corresponding function. 
   validation-value is here for possible future implementations but is not needed in the 
   uniqueness validation.
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
    (let [schema-json (generics/read-schema-config concept-type version)
          schema (json/parse-string schema-json true)]
      (fn [context concept]
        (validate-with-schema context concept schema)))
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
  (let [generic-types (concepts/get-generic-concept-types-array)
        validators (reduce (fn [vs concept-type]
                             (let [current-version (generics/current-generic-version concept-type)
                                   validator (load-schema-validation concept-type current-version)]
                               (if validator
                                 (assoc vs [concept-type current-version] validator)
                                 vs)))
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
        version (or (:version (extract-concept-metadata-spec concept))
                    ;; If version is not specified in metadata, use the current version
                    ;; of the concept type, though it is likey that the concept failed
                    ;; schema validation and we never reach this point. 
                    (generics/current-generic-version concept-type))

        ;; Get validation functions
        validators (get-validation-functions context)
        validator-fn (get validators [concept-type version])

        ;; Only run validation if a validator is defined
        errors (when validator-fn
                 (validator-fn context concept))]

    ;; Throw service errors if any validation errors are found
    (when (seq errors)
      (errors/throw-service-errors :invalid-data errors))))
