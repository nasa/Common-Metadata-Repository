(ns cmr.search.services.parameters
  "Contains functions for parsing and converting query parameters to query conditions"
  (:require [clojure.set :as set]
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
                 #(when % (set/rename-keys % param-aliases)))))

(def concept-param->type
  "A mapping of param names to query condition types based on concept-type"
  {:collection {:entry_title :string
                :provider :string
                :short_name :string
                :version :string
                :temporal :temporal
                :concept-id :string
                :campaign :string
                :two_d_coordinate_system_name :string}

   :granule {:granule_ur :string
             :collection_concept_id :string
             :provider :collection-query
             :entry_title :collection-query
             :attribute :attribute
             :short_name :collection-query
             :version :collection-query
             :temporal :temporal}})

(defn- param-name->type
  "Returns the query condition type based on the given concept-type and param-name."
  [concept-type param-name]
  (get-in concept-param->type [concept-type param-name]))

(defmulti parameter->condition
  "Converts a parameter into a condition"
  (fn [concept-type param value options]
    (param-name->type concept-type param)))

(defmethod parameter->condition :string
  [concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (qm/and-conds
        (map #(parameter->condition concept-type param % options) value))
      (qm/or-conds
        (map #(parameter->condition concept-type param % options) value)))
    (qm/map->StringCondition
      {:field param
       :value value
       :case-sensitive? (not= "true" (get-in options [param :ignore_case]))
       :pattern? (= "true" (get-in options [param :pattern]))})))

(defn parameters->query
  "Converts parameters into a query model."
  [concept-type params]
  (let [options (get params :options {})
        page-size (Integer. (get params :page_size qm/default-page-size))
        page-num (Integer. (get params :page_num qm/default-page-num))
        params (dissoc params :options :page_size :page_num)]
    (if (empty? params)
      (qm/query {:concept-type concept-type
                 :page-size page-size
                 :page-num page-num}) ;; matches everything
      ;; Convert params into conditions
      (let [conditions (map (fn [[param value]]
                              (parameter->condition concept-type param value options))
                            params)]
        (qm/query {:concept-type concept-type
                   :page-size page-size
                   :page-num page-num
                   :condition (qm/and-conds conditions)})))))

