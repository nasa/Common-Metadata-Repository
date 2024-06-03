(ns cmr.metadata-db.services.validation.util
  "General validators"
  (:require
   [cmr.common.services.errors :as errors]))

(defn build-validator
  "Creates a function that will call f with it's arguments. If f returns any
  errors then it will throw a service error of the type given."
  [error-type f]
  (fn [& args]
    (when-let [errors (apply f args)]
      (when (seq errors)
        (errors/throw-service-errors error-type errors)))))

(defn apply-validations
  "Given a list of validation functions, applies the arguments to each
  validation, concatenating all errors and returning them. As such, validation
  functions are expected to only return a list; if the list is empty, it is
  understood that no errors occurred."
  [validations & args]
  (reduce (fn [errors validation]
            (if-let [new-errors (apply validation args)]
              (concat errors new-errors)
              errors))
          []
          validations))

(defn compose-validations
  "Creates a function that will compose together a list of validation functions
  into a single function that will perform all validations together."
  [validation-fns]
  (partial apply-validations validation-fns))
