(ns cmr.metadata-db.services.provider-validation
  "Contains functions to validate provider."
  (:require [cmr.common.validations.core :as v]
            [cmr.common.services.errors :as errors]
            [clojure.string :as s]
            [cmr.metadata-db.services.messages :as msg]))

(def small-provider-id
  "Provider id of the small provider"
  "SMALL_PROV")

(def ^:private ^:const PROVIDER_ID_MAX_LENGTH 10)
(def ^:private ^:const PROVIDER_SHORT_NAME_MAX_LENGTH 128)

(defn- provider-id-length-validation
  "Validates the provider id isn't too long."
  [field-path provider-id]
  (when (> (count provider-id) PROVIDER_ID_MAX_LENGTH)
    {field-path [(msg/field-too-long provider-id PROVIDER_ID_MAX_LENGTH)]}))

(defn- provider-short-name-length-validation
  "Validates the provider short name isn't too long."
  [field-path short-name]
  (when (> (count short-name) PROVIDER_SHORT_NAME_MAX_LENGTH)
    {field-path [(msg/field-too-long short-name PROVIDER_SHORT_NAME_MAX_LENGTH)]}))

(defn- field-blank-validation
  "Validates the string value isn't blank."
  [field-path value]
  (when (s/blank? value)
    {field-path [(msg/field-blank)]}))

(defn- provider-id-format-validation
  "Validates the provider id is in the correct format."
  [field-path provider-id]
  (when (and provider-id (not (re-matches #"^[A-Z][A-Z0-9_]*" provider-id)))
    {field-path [(msg/invalid-provider-id provider-id)]}))

(defn- provider-id-reserved-validation
  "Validates the provider id isn't SMALL_PROV which is reserved."
  [field-path provider-id]
  (when (= small-provider-id provider-id)
    {field-path [(msg/provider-id-reserved)]}))

(defn- must-be-boolean
  "Validates the value given is of Boolean type."
  [field-path value]
  (when-not (or (= true value) (= false value))
    {field-path [(format "%%s must be either true or false but was [%s]" (pr-str value))]}))

(def ^:private provider-validations
  {:provider-id (v/first-failing provider-id-length-validation
                                 field-blank-validation
                                 provider-id-format-validation
                                 provider-id-reserved-validation)
   :short-name (v/first-failing provider-short-name-length-validation
                                field-blank-validation)
   :cmr-only (v/first-failing v/required must-be-boolean)
   :small (v/first-failing v/required must-be-boolean)})

(defn validate-provider
  "Validates the provider. Throws an exception with validation errors if the provider is invalid."
  [provider]
  (let [errors (v/validate provider-validations provider)]
    (when (seq errors)
      (errors/throw-service-errors :bad-request (v/create-error-messages errors)))))

