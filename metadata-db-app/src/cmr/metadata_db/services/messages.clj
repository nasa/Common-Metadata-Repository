(ns cmr.metadata-db.services.messages
  (:require [clojure.string :as str]
            [camel-snake-kebab.core :as csk]))

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

(defn concept-id-and-revision-id-conflict [concept-id revision-id]
  (format "Conflict with existing concept-id [%s] and revision-id [%s]" concept-id revision-id))

(defn missing-concept-type []
  "Concept must include concept-type.")

(defn missing-provider-id []
  "Concept must include provider-id.")

(defn missing-native-id []
  "Concept must include native-id.")

(defn missing-extra-fields []
  "Concept must include extra-fields")

(defn invalid-tombstone-field [field]
  (format "Tombstone concept cannot include [%s]" (name field)))

(defn missing-concept-id-field []
  "Concept must include concept-id.")

(defn missing-extra-field [field]
  (format "Concept must include extra-field value for field [%s]" (name field)))

(defn nil-field [field]
  (format "Concept field [%s] cannot be nil." (name field)))

(defn find-not-supported [concept-type params]
  (format "Finding concept type [%s] with parameters [%s] is not supported."
          (name concept-type)
          (str/join ", " (map name params))))

(defn find-not-supported-combination [concept-type params]
  (format "Finding concept type [%s] with parameter combination [%s] is not supported."
          (name concept-type)
          (str/join ", " (map name params))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Concept Constraint Messages

(defn duplicate-field-msg
  "Returns an error message to use for concepts which violate the given unique field constraint.
  Note that the field must be a key within :extra-fields for a concept."
  [field concepts]
  (format
    "The %s [%s] must be unique. The following concepts with the same %s were found: [%s]."
    (str/replace (csk/->Camel_Snake_Case_String field) #"_" " ")
    (-> concepts first :extra-fields field)
    (str/replace (csk/->snake_case_string field) #"_" " ")
    (str/join ", " (map :concept-id concepts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Provider Messages

(defn provider-id-parameter-required []
  "A provider parameter was required but was not provided.")

(defn provider-small-field-cannot-be-modified [provider-id]
  (format "Provider [%s] small field cannot be modified." provider-id))

(defn small-provider-cannot-be-deleted []
  "Provider [SMALL_PROV] is a reserved provider of CMR and cannot be deleted.")

(defn provider-does-not-exist [provider-id]
  (format "Provider with provider-id [%s] does not exist."
          provider-id))

(defn providers-do-not-exist [provider-ids]
  (format "Providers with provider-ids [%s] do not exist."
          (str/join ", " provider-ids)))

(defn provider-with-id-exists [provider-id]
  (format "Provider with provider id [%s] already exists." provider-id))

(defn provider-with-short-name-exists [provider]
  (let [{:keys [provider-id short-name]} provider]
    (format "Provider with short name [%s] already exists. Its provider id is [%s]."
            short-name provider-id)))

(defn field-too-long [value limit]
  (format "%%s [%s] exceeds %d characters" value limit))

(defn provider-id-reserved []
  "%s [SMALL_PROV] is reserved")

(defn invalid-provider-id [provider-id]
  (format "%%s [%s] is invalid" provider-id))
