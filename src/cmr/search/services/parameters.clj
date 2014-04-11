(ns cmr.search.services.parameters
  "Contains functions for parsing and validating query parameters"
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [cmr.common.services.errors :as err]
            [cmr.search.models.query :as qm]))

(def param-aliases
  "A map of non UMM parameter names to their UMM fields."
  {:dataset_id :entry_title})

(defn replace-parameter-aliases
  "Replaces aliases of parameter names"
  [params]
  (-> params
      (set/rename-keys param-aliases)
      (update-in [:options]
                 #(when % (clojure.set/rename-keys % param-aliases)))))

(def concept-param->type
  "A mapping of param names to query condition types based on concept-type"
  {:collection {:entry_title :string
                :provider :string
                :short_name :string
                :version :string
                :temporal :temporal}

   :granule {:granule_ur :string
             :collection_concept_id :string
             :provider :collection-query
             :entry_title :collection-query}})

(defn- param-name->type
  "Returns the query condition type based on the given concept-type and param-name."
  [concept-type param-name]
  (param-name (concept-type concept-param->type)))

(defn- valid-param-names
  "A set of the valid parameter names for the given concept-type."
  [concept-type]
  (set (concat (keys (concept-type concept-param->type)) (keys param-aliases) [:options])))

(defn unrecognized-params-validation
  "Validates that no invalid parameters were supplied"
  [concept-type params]
  (map #(str "Parameter [" (name % )"] was not recognized.")
       (clojure.set/difference (set (keys params))
                               (valid-param-names concept-type))))

(defn unrecognized-params-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (map #(str "Parameter [" (name %)"] with option was not recognized.")
         (clojure.set/difference (set (keys options))
                                 (valid-param-names concept-type)))
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
  [concept-type params]
  (let [errors (mapcat #(% concept-type params) parameter-validations)]
    (when-not (empty? errors)
      (err/throw-service-errors :invalid-data errors)))
  params)

(defmulti parameter->condition
  "Converts a parameter into a condition"
  (fn [concept-type param value options]
    (param-name->type concept-type param)))

(defmethod parameter->condition :string
  [concept-type param value options]
  (if (sequential? value)
    (qm/or-conds
      (map #(parameter->condition concept-type param % options) value))
    (qm/map->StringCondition
      {:field param
       :value value
       :case-sensitive? (not= "true" (get-in options [param :ignore_case]))
       :pattern? (= "true" (get-in options [param :pattern]))})))

(defn parameters->query
  "Converts parameters into a query model."
  [concept-type params]
  (let [options (get params :options {})
        params (dissoc params :options)]
    (if (empty? params)
      (qm/query concept-type) ;; matches everything
      ;; Convert params into conditions
      (let [conditions (map (fn [[param value]]
                              (parameter->condition concept-type param value options))
                            params)]
        (qm/query concept-type (qm/and-conds conditions))))))

