(ns cmr.metadata-db.services.concept-validations
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.common.concepts :as cc]
            [clojure.set :as set]
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

(def concept-type->required-extra-fields
  "A map of concept type to the required extra fields"
  {:collection #{:short-name :version-id :entry-title}
   :granule #{:parent-collection-id}})

(defn extra-fields-missing-validation
  [concept]
  (if-let [extra-fields (:extra-fields concept)]
    (map #(msg/missing-extra-field %)
         (set/difference (concept-type->required-extra-fields (:concept-type concept))
                         (set (keys extra-fields))))
    [(msg/missing-extra-fields)]))

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

(def concept-validations
  [concept-type-missing-validation
   provider-id-missing-validation
   native-id-missing-validation
   concept-id-validation
   extra-fields-missing-validation
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