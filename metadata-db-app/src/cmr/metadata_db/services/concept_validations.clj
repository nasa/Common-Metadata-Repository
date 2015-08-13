(ns cmr.metadata-db.services.concept-validations
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.common.concepts :as cc]
            [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.common.date-time-parser :as p]
            [cmr.common.util :as util]))

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
    [(msg/missing-concept-id)]))

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
             (dissoc concept :revision-date :revision-id)))

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

(defn concept-id-match-fields-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (when-not (cc/concept-id-validation concept-id)
      (let [{:keys [concept-type provider-id]} (cc/parse-concept-id concept-id)]
        (when-not (and (= concept-type (:concept-type concept))
                       (= provider-id (:provider-id concept)))
          [(msg/invalid-concept-id concept-id (:provider-id concept) (:concept-type concept))])))))


(def concept-validation
  "Validates a concept and returns a list of errors"
  (util/compose-validations [concept-type-missing-validation
                             provider-id-missing-validation
                             native-id-missing-validation
                             concept-id-validation
                             extra-fields-missing-validation
                             nil-fields-validation
                             nil-extra-fields-validation
                             concept-id-match-fields-validation
                             (datetime-validator [:revision-date])
                             (datetime-validator [:extra-fields :delete-time])]))

(def validate-concept
  "Validates a concept. Throws an error if invalid."
  (util/build-validator :invalid-data concept-validation))

(def valid-tombstone-keys
  #{:concept-id :revision-id :revision-date :concept-type :deleted})

(defn validate-tombstone-keys
  "Validates that there are no extraneous keys"
  [tombstone]
  (map #(msg/invalid-tombstone-field %)
       (set/difference (set (keys tombstone))
                       valid-tombstone-keys)))

(def tombstone-request-validation
  (util/compose-validations [concept-id-missing-validation
                             validate-tombstone-keys]))

(def validate-tombstone-request
  (util/build-validator :invalid-data tombstone-request-validation))
