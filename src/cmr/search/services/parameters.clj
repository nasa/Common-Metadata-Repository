(ns cmr.search.services.parameters
  "Contains functions for parsing and validating query parameters"
  (:require [clojure.set]
            [clojure.string :as s]
            [cmr.common.services.errors :as err]
            [cmr.search.models.query :as qm]))

(def param-name->type
  "A map of parameter names to types so that we can appropriately parse or validate them."
  {:entry_title :string
   :provider :string})

(def param-aliases
  "A map of parameter name aliases to their parameter name."
  {:dataset_id :entry_title})

(def valid-param-names
  "A set of the valid parameter names."
  (set (concat (keys param-name->type) (keys param-aliases) [:options])))

(defn unrecognized-params-validation
  "Validates that no invalid parameters were supplied"
  [params]
  (map #(str "Parameter [" (name % )"] was not recognized.")
       (clojure.set/difference (set (keys params))
                               valid-param-names)))

(def parameter-validations
  "A list of the functions that can validate parameters. They all accept parameters as an argument
  and return a list of errors."
  [unrecognized-params-validation])

(defn validate-parameters
  "Validates parameters. Throws exceptions to send to the user. Returns parameters if validation
  was successful so it can be chained with other calls."
  [params]
  (when-let [errors (seq (reduce (fn [errors validation]
                                   (concat errors (validation params)))
                                 []
                                 parameter-validations))]
    (err/throw-service-errors :invalid-data errors))
  params)

(defmulti parameter->condition
  "Converts a parameter into a condition"
  (fn [param value options]
    (param-name->type param)))

(defmethod parameter->condition :string
  [param value options]
  (if (sequential? value)
    (qm/or-conds
      (map #(parameter->condition param % options) value))
    (qm/map->StringCondition
      {:field param
       :value value
       :case-sensitive? (not= "true" (get-in options [param :ignore_case]))
       :pattern? (= "true" (get-in options [param :pattern]))})))

(defn parameters->query
  "Converts parameters into a query model."
  [concept-type params]

  (if (empty? params)
    (qm/query concept-type) ;; matches everything
    (let [options (get params :options {})
          params (dissoc params :options)

          ;; Correct key names for aliases
          options (clojure.set/rename-keys options param-aliases)
          params (clojure.set/rename-keys params param-aliases)

          ;; Convert params into conditions
          conditions (map (fn [[param value]]
                            (parameter->condition param value options))
                          params)]
      (qm/query concept-type (qm/and-conds conditions)))))

