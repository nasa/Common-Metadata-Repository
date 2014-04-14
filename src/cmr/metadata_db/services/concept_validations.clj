(ns cmr.metadata-db.services.concept-validations
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.common.concepts :as cc]
            [cmr.common.services.errors :as errors]))

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

(defn concept-id-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (cc/validate-concept-id concept-id)))

(defn concept-id-match-fields-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (when-not (cc/validate-concept-id concept-id)
      (let [{:keys [concept-type provider-id]} (cc/parse-concept-id concept-id)]
        (when-not (and (= concept-type (:concept-type concept))
                       (= provider-id (:provider-id concept)))
          [(msg/invalid-concept-id concept-id (:provider-id concept) (:concept-type concept))])))))

(def concept-validations
  [concept-type-missing-validation
   provider-id-missing-validation
   native-id-missing-validation
   concept-id-validation
   concept-id-match-fields-validation])

(defn concept-validation
  "Validates a concept and returns a list of errors"
  [concept]
  (reduce (fn [errors validation]
            (if-let [new-errors (validation concept)]
              (concat errors new-errors)
              errors))
          []
          concept-validations))

(defn validate-concept
  "Validates a concept. Throws an error if invalid."
  [concept]
  (let [errors (concept-validation concept)]
    (when (> (count errors) 0)
      (errors/throw-service-errors :invalid-data errors))))