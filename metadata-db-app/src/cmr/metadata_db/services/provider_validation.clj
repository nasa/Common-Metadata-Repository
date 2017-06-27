(ns cmr.metadata-db.services.provider-validation
  "Contains functions to validate provider."
  (:require [cmr.common.validations.core :as v]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as cmsg]
            [clojure.string :as s]
            [cmr.metadata-db.services.messages :as msg]))

(def small-provider-id
  "Provider id of the small provider"
  "SMALL_PROV")

(def cmr-provider
  "The system level CMR provider for tags"
  {:provider-id "CMR"
   :short-name "CMR"
   :system-level? true
   :cmr-only true
   :small false})

(def ^:const PROVIDER_ID_MAX_LENGTH 10)
(def ^:const PROVIDER_SHORT_NAME_MAX_LENGTH 128)

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
    {field-path ["%s cannot be blank"]}))

(defn- provider-id-format-validation
  "Validates the provider id is in the correct format."
  [field-path provider-id]
  (when (and provider-id (not (re-matches #"^[A-Z][A-Z0-9_]*" provider-id)))
    {field-path [(msg/invalid-provider-id provider-id)]}))

(defn- reserved-provider-id?
  "Boolean indicating whether or not the given provider-id is reserved to the system"
  [provider-id]
  (boolean (or (= small-provider-id provider-id) (= (:provider-id cmr-provider) provider-id))))

(defn- provider-id-reserved-validation
  "Validates the provider id isn't SMALL_PROV or CMR which are reserved."
  [field-path provider-id]
  (when (reserved-provider-id? provider-id)
    {field-path [(msg/provider-id-reserved provider-id)]}))

(defn validate-provider-id-deletion
  "Validates that the provider is is not a reserved id"
  [provider-id]
  (when (reserved-provider-id? provider-id)
    (cmsg/data-error :bad-request msg/reserved-provider-cannot-be-deleted provider-id)))

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
  (v/validate! provider-validations provider))

