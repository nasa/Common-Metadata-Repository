(ns cmr.metadata-db.services.messages
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [cmr.common.validations.core :as validations]))

(defn missing-concept-id [concept-type provider-id native-id]
  (format
    "Concept with concept-type [%s] provider-id [%s] native-id [%s] does not exist."
    (name concept-type)
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
  (format "Conflict with existing concept-id [%s] and revision-id [%s]"
          concept-id
          revision-id))

(defn missing-concept-type []
  "Concept must include concept-type.")

(defn missing-provider-id []
  "Concept must include provider-id.")

(defn missing-native-id []
  "Concept must include native-id.")

(defn missing-concept-id-field []
  "Concept must include concept-id.")

(defn missing-extra-fields []
  "Concept must include extra-fields")

(defn subscription-not-found [field]
  (format "Subscription concept [%s] not found." (name field)))

(defn invalid-tombstone-field [field]
  (format "Tombstone concept cannot include [%s]" (name field)))

(defn missing-concept-id-field []
  "Concept must include concept-id.")

(defn missing-extra-field [field]
  (format "Concept must include extra-field value for field [%s]" (name field)))

(defn invalid-tombstone-field [field]
  (format "Tombstone concept cannot include [%s]" (name field)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Concept Constraint Messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn duplicate-field-msg
  "Returns an error message to use for concepts which violate the given unique field constraint.
  Note that the field must be a key within :extra-fields for a concept."
  [field concepts]
  (case field
    :entry-id (format (str "The Short Name [%s] and Version Id [%s] combined must be unique. "
                           "The following concepts with the same Short Name and Version Id were found: [%s].")
                      (-> concepts first :extra-fields :short-name)
                      (-> concepts first :extra-fields :version-id)
                      (str/join ", " (map :concept-id concepts)))
    :fingerprint (format (str "The Fingerprint of the variable which is defined by the variable's "
                              "Instrument short name, variable short name, units and dimensions "
                              "must be unique. The following variable with the same fingerprint "
                              "but different native id was found: [%s].")
                         (str/join ", " (map :concept-id concepts)))
    (format
     "The %s [%s] must be unique. The following concepts with the same %s were found: [%s]."
     (str/replace (csk/->Camel_Snake_Case_String field) #"_" " ")
     (-> concepts first :extra-fields field)
     (str/replace (csk/->snake_case_string field) #"_" " ")
     (str/join ", " (map :concept-id concepts)))))

(defn concept-higher-transaction-id
  [revision-id concept-id transaction-id this-revision-id this-transaction-id]
  (format (str "Revision [%d] of concept [%s] has transaction-id [%d] "
               "which is higher than revision [%d] with transaction-id [%d].")
          revision-id
          concept-id
          transaction-id
          this-revision-id
          this-transaction-id))

(defn concept-lower-transaction-id
  [revision-id concept-id transaction-id this-revision-id this-transaction-id]
  (format (str "Revision [%d] of concept [%s] has transaction-id [%d] "
               "which is lower than revision [%d] with transaction-id [%d].")
          revision-id
          concept-id
          transaction-id
          this-revision-id
          this-transaction-id))

(defn pfn-equality-failure
  [field-type concept]
  (let [humanized-field (validations/humanize-field field-type)]
    (format (str "The Provider Id [%s] and %s [%s] combination must be "
                 "unique for a given native-id. The following concept "
                 "with the same Provider Id and %s was found: [%s].")
            (:provider-id concept)
            humanized-field
            (get-in concept [:extra-fields field-type])
            humanized-field
            (:concept-id concept))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Provider Messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn provider-id-parameter-required []
  "A provider parameter was required but was not provided.")

(defn provider-small-field-cannot-be-modified [provider-id]
  (format "Provider [%s] small field cannot be modified." provider-id))

(defn reserved-provider-cannot-be-deleted [provider-id]
  (format "Provider [%s] is a reserved provider of CMR and cannot be deleted." provider-id))

(defn cmr-provider-cannot-be-deleted []
  "Provider [CMR] is a reserved provider of CMR and cannot be deleted.")

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
            short-name
            provider-id)))

(defn granule-collection-cannot-change [old-concept-id new-concept-id]
  (format "Granule's parent collection cannot be changed, was [%s], now [%s]."
          old-concept-id new-concept-id))

(defn field-too-long [value limit]
  (format "%%s [%s] exceeds %d characters" value limit))

(defn provider-id-reserved [provider-id]
  (format "%%s [%s] is reserved" provider-id))

(defn invalid-provider-id [provider-id]
  (format "%%s [%s] is invalid" provider-id))

(defn tags-only-system-level
  [provider-id]
  (format "Tag could not be associated with provider [%s]. Tags are system level entities."
          provider-id))

(defn tag-associations-only-system-level
  [provider-id]
  (format (str "Tag association could not be associated with provider [%s]. Tag associations are "
               "system level entities.")
          provider-id))

(defn humanizers-only-system-level
  [provider-id]
  (format "Humanizer could not be associated with provider [%s]. Humanizer is system level entity."
          provider-id))

(defn variable-associations-only-system-level
  [provider-id]
  (format (str "Variable association could not be associated with provider [%s]. "
               "Variable associations are system level entities.")
          provider-id))

(defn service-associations-only-system-level
  [provider-id]
  (format (str "Service association could not be associated with provider [%s]. "
               "Service associations are system level entities.")
          provider-id))

(defn tool-associations-only-system-level
  [provider-id]
  (format (str "Tool association could not be associated with provider [%s]. "
               "Tool associations are system level entities.")
          provider-id))

(defn concept-not-found
  [provider-id field-name field-value]
  (format "Unable to find saved concept for provider [%s] and %s [%s]"
          provider-id
          field-name
          field-value))

(defn delete-association-not-found
  [assoc-type native-id]
  (let [[identifier concept-id revision-id] (str/split native-id #"/")]
    (if revision-id
      (format (str "%s [%s] is not associated with the specific collection concept revision "
                   "concept id [%s] and revision id [%s].")
              (str/capitalize (name assoc-type)) identifier concept-id revision-id)
      (format "%s [%s] is not associated with collection [%s]."
              (str/capitalize (name assoc-type)) identifier concept-id))))
