(ns cmr.metadata-db.services.messages
  (:require [clojure.string :as str]))

(defn missing-concept-id [concept-type provider-id native-id]
  (format
    "Concept with concept-type [%s] provider-id [%s] native-id [%s] does not exist."
    concept-type
    provider-id
    native-id))

(defn concept-does-not-exist [concept-id]
  (format
    "Concept with concept-id [%s] does not exist."
    concept-id))

(defn concept-with-concept-id-and-rev-id-does-not-exist [concept-id revision-id]
  (format
    "Concept with concept-id [%s] and revision-id [%s] does not exist."
    concept-id revision-id))

(defn invalid-revision-id [concept-id expected-id received-id]
  (format
    "Expected revision-id of [%s] got [%s] for [%s]"
    expected-id received-id concept-id))

(defn invalid-revision-id-unknown-expected [revision-id]
  (format
    "Invalid revision-id [%s]"
    revision-id))

(defn missing-concept-type []
  "Concept must include concept-type.")

(defn missing-provider-id []
  "Concept must include provider-id.")

(defn missing-native-id []
  "Concept must include native-id.")

(defn missing-extra-fields []
  "Concept must include extra-fields")

(defn missing-extra-field [field]
  (format "Concept must include extra-field value for field [%s]" (name field)))

(defn nil-field [field]
  (format "Concept field [%s] cannot be nil." (name field)))

(defn find-not-supported [concept-type params]
  (format "Finding concept type [%s] with parameter combination [%s] is not supported."
          (name concept-type)
          (str/join ", " params)))

(defn invalid-concept-id [concept-id provider-id concept-type]
  (format "Concept-id [%s] for concept does not match provider-id [%s] or concept-type [%s]."
          concept-id
          provider-id
          concept-type))

(defn concept-exists-with-different-id
  [existing-concept-id existing-native-id given-concept-id given-native-id  concept-type provider-id]
  (format
    (str "A concept with concept-id [%s] and native-id [%s] already exists for concept-type [%s] "
         "provider-id [%s]. The given concept-id [%s] and native-id [%s] would conflict with that one.")
    existing-concept-id
    existing-native-id
    concept-type
    provider-id
    given-concept-id
    given-native-id))

(defn maximum-save-attempts-exceeded [error-msg]
  (str "Reached limit of attempts to save concept - giving up. Potential cause: " error-msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Concept Constraint Messages

(defmulti duplicate-field-msg
  "Returns an error message to use for concepts which violate the given unique field constraint."
  (fn [field concepts]
    field))

(defmethod duplicate-field-msg :entry-title
  [field concepts]
  (format
    "The Entry Title [%s] must be unique. The following concepts with the same entry title were found: [%s]."
    (-> concepts first :extra-fields :entry-title)
    (str/join ", " (map :concept-id concepts))))

(defmethod duplicate-field-msg :entry-id
  [field concepts]
  (format
    "The Entry Id [%s] must be unique. The following concepts with the same entry id were found: [%s]."
    (-> concepts first :extra-fields :entry-id)
    (str/join ", " (map :concept-id concepts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Provider Messages

(defn provider-id-parameter-required []
  "A provider parameter was required but was not provided.")

(defn provider-does-not-exist [provider-id]
  (format "Provider with provider-id [%s] does not exist."
          provider-id))

(defn providers-do-not-exist [provider-ids]
  (format "Providers with provider-ids [%s] do not exist."
          (str/join ", " provider-ids)))

(defn provider-exists [provider-id]
  (format "Provider [%s] already exists."
          provider-id))

(defn provider-id-empty [provider-id]
  (format "Provider ID cannot be empty"))

(defn provider-id-too-long [provider-id]
  (format "Provider ID [%s] exceeds ten characters"
          provider-id))

(defn invalid-provider-id [provider-id]
  (format "provider-id [%s] is invalid" provider-id))
