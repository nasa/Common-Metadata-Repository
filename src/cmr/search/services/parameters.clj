(ns cmr.search.services.parameters
  "Contains functions for parsing and validating query parameters"
  (:require [clojure.set]
            [clojure.string :as s]
            [cmr.common.services.errors :as err]
            [cmr.search.models.query :as qm]))

(def param-name->type
  "A map of parameter names to types so that we can appropriately parse or validate them."
  {:dataset_id :string})

(def valid-param-names
  "A set of the valid parameter names."
  (set (keys param-name->type)))

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
    (err/invalid-data-errors! errors))
  params)

;; FIXME we need to handle case sensitive, patterns, and multiple

(defmulti parameter->condition
  "Converts a parameter into a condition"
  (fn [param value options]
    (param-name->type param)))

(defmethod parameter->condition :string
  [param value options]
  (qm/map->StringCondition
    {:field param
     :value value
     :case-sensitive? true
     :pattern false}))


(defn parameters->query
  "Converts parameters into a query model."
  [concept-type params]

  (let [options (or (dissoc params :options) {})
        conditions (map (fn [[param value]]
                          (parameter->condition param value options))
                        params)]
    (qm/query concept-type (qm/and-conds conditions))))

