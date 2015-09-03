(ns cmr.metadata-db.services.concept-validations
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.metadata-db.services.provider-service :as providers]
            [cmr.common.concepts :as cc]
            [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.common.date-time-parser :as p]
            [cmr.common.util :as util]))

;;TODO add validation of concept types with provider type
;; system level providers support :tag
;; normal providers support :collection and :granule

(defn concept-type-missing-validation
  [concept]
  (when-not (:concept-type concept)
    [(msg/missing-concept-type)]))

(defn provider-id-missing-validation
  [concept]
  (when-not (:provider-id concept)
    [(msg/missing-provider-id)]))

(defn native-id-missing-validation
  [concept]
  (when-not (:native-id concept)
    [(msg/missing-native-id)]))

(defn concept-id-missing-validation
  [concept]
  (when-not (:concept-id concept)
    [(msg/missing-concept-id-field)]))

(def concept-type->required-extra-fields
  "A map of concept type to the required extra fields"
  {:collection #{:short-name :version-id :entry-id :entry-title}
   :granule #{:parent-collection-id :granule-ur}})

(defn extra-fields-missing-validation
  "Validates that the concept is provided with extra fields and that all of them are present and not nil."
  [concept]
  (if-let [extra-fields (:extra-fields concept)]
    (map #(msg/missing-extra-field %)
         (set/difference (concept-type->required-extra-fields (:concept-type concept))
                         (set (keys extra-fields))))
    [(msg/missing-extra-fields)]))

(defn nil-fields-validation
  "Validates that none of the fields are nil."
  [concept]
  (reduce-kv (fn [acc field value]
               (if (nil? value)
                 (conj acc (msg/nil-field field))
                 acc))
             []
             (dissoc concept :revision-date :revision-id :user-id)))

(defn datetime-validator
  [field-path]
  (fn [concept]
    (when-let [value (get-in concept field-path)]
      (try
        (p/parse-datetime value)
        nil
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (:errors data)))))))

(defn nil-extra-fields-validation
  "Validates that among the extra fields, only delete-time and version-id can sometimes be nil."
  [concept]
  (nil-fields-validation (apply dissoc (:extra-fields concept) [:delete-time :version-id])))

(defn concept-id-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (cc/concept-id-validation concept-id)))

(defn tag-concept-id-match-fields-validation
  "Validate that the concept-id is a match for the values in the concept fields"
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (when-not (cc/concept-id-validation concept-id)
      (let [{:keys [concept-type _]} (cc/parse-concept-id concept-id)]
        (when-not (= concept-type (:concept-type concept))
          [(msg/invalid-concept-id concept-id "CMR" (:concept-type concept))])))))

(defn concept-id-match-fields-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (when-not (cc/concept-id-validation concept-id)
      (let [{:keys [concept-type provider-id]} (cc/parse-concept-id concept-id)]
        (when-not (and (= concept-type (:concept-type concept))
                       (= provider-id (:provider-id concept)))
          [(msg/invalid-concept-id concept-id (:provider-id concept) (:concept-type concept))])))))

(def ^:private base-concept-validations
  "Validations for all concept types"
  [concept-type-missing-validation
   native-id-missing-validation
   concept-id-validation
   nil-fields-validation
   nil-extra-fields-validation
   (datetime-validator [:revision-date])
   (datetime-validator [:extra-fields :delete-time])])

(def default-concept-validation
  "Builds a function that validates a concept and returns a list of errors"
  (util/compose-validations
    (conj base-concept-validations
          extra-fields-missing-validation
          concept-id-match-fields-validation
          provider-id-missing-validation)))

(def tag-concept-validation
  "Creates a function tht validates a tag concept and returns a listg of errors"
  (util/compose-validations (conj base-concept-validations tag-concept-id-match-fields-validation)))

(def validate-concept-default
  "Validates a concept. Throws an error if invalid."
  (util/build-validator :invalid-data default-concept-validation))

(def validate-concept-tag
  "validates a tag concept. Throws an error if invalid."
  (util/build-validator :invalid-data tag-concept-validation))

(defmulti validate-concept
  "Validates a concept. Throws an error if invalid."
  (fn [concept]
    (:concept-type concept)))

(defmethod validate-concept :tag
  [concept]
  (validate-concept-tag concept))

(defmethod validate-concept :default
  [concept]
  (validate-concept-default concept))

(def valid-tombstone-keys
  #{:concept-id :revision-id :revision-date :concept-type :deleted :user-id})

(defn validate-tombstone-keys
  "Validates that there are no extraneous keys"
  [tombstone]
  (map msg/invalid-tombstone-field
       (set/difference (set (keys tombstone))
                       valid-tombstone-keys)))

(def tombstone-request-validation
  (util/compose-validations [concept-id-missing-validation
                             validate-tombstone-keys]))

(def validate-tombstone-request
  (util/build-validator :invalid-data tombstone-request-validation))
