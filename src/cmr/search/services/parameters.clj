(ns cmr.search.services.parameters
  "Contains functions for parsing and validating query parameters"
  (:require [clojure.set]
            [clojure.string :as s]
            [cmr.common.services.errors :as err]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameter-converters.temporal :as temporal-converter]))

(def param-aliases
  "A map of non UMM parameter names to their UMM fields."
  {:dataset_id :entry_title})

(defn replace-parameter-aliases
  "Replaces aliases of parameter names"
  [params]
  (-> params
      (clojure.set/rename-keys param-aliases)
      (update-in [:options]
                 #(when % (clojure.set/rename-keys % param-aliases)))))

(def param-name->type
  "A map of parameter names to types so that we can appropriately parse or validate them."
  {:entry_title :string
   :provider :string
   :short_name :string
   :version :string
   :temporal :temporal})

(def valid-param-names
  "A set of the valid parameter names."
  (set (concat (keys param-name->type) (keys param-aliases) [:options])))

(defn unrecognized-params-validation
  "Validates that no invalid parameters were supplied"
  [params]
  (map #(str "Parameter [" (name % )"] was not recognized.")
       (clojure.set/difference (set (keys params))
                               valid-param-names)))

(defn unrecognized-params-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [params]
  (if-let [options (:options params)]
    (map #(str "Parameter [" (name %)"] with option was not recognized.")
         (clojure.set/difference (set (keys options))
                                 valid-param-names))
    []))

(defn options-only-for-string-conditions-validation
  "Validates that only string conditions support options"
  [params]
  ;; TODO once we have more conditions than string conditions add a validation that only
  ;; string conditions support options.
  [])

(defn unrecognized-params-settings-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [params]
  (if-let [options (:options params)]
    (apply concat
           (map
             (fn [[param settings]]
               (map #(str "Option [" (name %) "] for param [" (name param) "] was not recognized." )
                    (clojure.set/difference (set (keys settings)) (set [:ignore_case :pattern]))))
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

(defmethod parameter->condition :temporal
  [param value options]
  (temporal-converter/parameter->condition param value))

(defn parameters->query
  "Converts parameters into a query model."
  [concept-type params]
  (let [options (get params :options {})
        params (dissoc params :options)]
    (if (empty? params)
      (qm/query concept-type) ;; matches everything
      ;; Convert params into conditions
      (let [conditions (map (fn [[param value]]
                              (parameter->condition param value options))
                            params)]
        (qm/query concept-type (qm/and-conds conditions))))))

