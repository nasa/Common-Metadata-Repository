(ns cmr.search.services.parameter-validation
  "Contains functions for validating query parameters"
  (:require [clojure.set :as set]
            [cmr.common.services.errors :as err]
            [cmr.search.services.parameters :as p]))

(defn- concept-type->valid-param-names
  "A set of the valid parameter names for the given concept-type."
  [concept-type]
  (set (concat
         (keys (get p/concept-param->type concept-type))
         (keys p/param-aliases)
         [:options])))

(defn unrecognized-params-validation
  "Validates that no invalid parameters were supplied"
  [concept-type params]
  (map #(str "Parameter [" (name % )"] was not recognized.")
       (set/difference (set (keys params))
                       (concept-type->valid-param-names concept-type))))

(defn unrecognized-params-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (map #(str "Parameter [" (name %)"] with option was not recognized.")
         (set/difference (set (keys options))
                         (concept-type->valid-param-names concept-type)))
    []))

(defn options-only-for-string-conditions-validation
  "Validates that only string conditions support options"
  [concept-type params]
  ;; TODO once we have more conditions than string conditions add a validation that only
  ;; string conditions support options.
  [])

(defn unrecognized-params-settings-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (apply concat
           (map
             (fn [[param settings]]
               (map #(str "Option [" (name %) "] for param [" (name param) "] was not recognized.")
                    (set/difference (set (keys settings)) (set [:ignore_case :pattern]))))
             options))
    []))

(def parameter-validations
  "A list of the functions that can validate parameters. They all accept parameters as an argument
  and return a list of errors."
  [unrecognized-params-validation
   unrecognized-params-in-options-validation
   options-only-for-string-conditions-validation
   unrecognized-params-settings-in-options-validation])

(defn validate-parameters
  "Validates parameters. Throws exceptions to send to the user. Returns parameters if validation
  was successful so it can be chained with other calls."
  [concept-type params]
  (let [errors (mapcat #(% concept-type params) parameter-validations)]
    (when-not (empty? errors)
      (err/throw-service-errors :invalid-data errors)))
  params)
