(ns cmr.search.services.parameters
  "Contains functions for parsing and converting query parameters to query conditions"
  (:require [clojure.set :as set]
            [cmr.search.models.query :as qm]
            [cmr.common.util :as u]))

(def param-aliases
  "A map of non UMM parameter names to their UMM fields."
  {:dataset-id :entry-title
   :dif-entry-id :entry-id
   :campaign :project
   :online-only :downloadable})

(defn replace-parameter-aliases
  "Replaces aliases of parameter names"
  [params]
  (-> params
      (set/rename-keys param-aliases)
      (update-in [:options]
                 #(when % (set/rename-keys % param-aliases)))))

(def concept-param->type
  "A mapping of param names to query condition types based on concept-type"
  {:collection {:entry-title :string
                :entry-id :string
                :provider :string
                :short-name :string
                :version :string
                :temporal :temporal
                :concept-id :string
                :project :string
                :archive-center :string
                :two-d-coordinate-system-name :string}
   :granule {:granule-ur :string
             :collection-concept-id :string
             :producer-granule-id :string
             :readable-granule-name :readable-granule-name
             :provider :collection-query
             :entry-title :collection-query
             :attribute :attribute
             :short-name :collection-query
             :version :collection-query
             :temporal :temporal
             :project :string
             :concept-id :string
             :downloadable :boolean}})

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
       :case-sensitive? (not= "true" (get-in options [param :ignore-case]))
       :pattern? (= "true" (get-in options [param :pattern]))})))

(defmethod parameter->condition :boolean
  [concept-type param value options]
  (if (or (= "true" value) (= "false" value))
    (qm/map->BooleanCondition {:field param
                               :value (= "true" value)})
    (qm/->MatchAllCondition)))

(defmethod parameter->condition :readable-granule-name
  [concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (qm/and-conds
        (map #(parameter->condition concept-type param % options) value))
      (qm/or-conds
        (map #(parameter->condition concept-type param % options) value)))
    (qm/or-conds
      [(qm/map->StringCondition
         {:field :granule-ur
          :value value
          :case-sensitive? (not= "true" (get-in options [param :ignore-case]))
          :pattern? (= "true" (get-in options [param :pattern]))})
       (qm/map->StringCondition
         {:field :producer-granule-id
          :value value
          :case-sensitive? (not= "true" (get-in options [param :ignore-case]))
          :pattern? (= "true" (get-in options [param :pattern]))})])))

(defn parameters->query
  "Converts parameters into a query model."
  [concept-type params]
  (let [options (u/map-keys->kebab-case (get params :options {}))
        page-size (Integer. (get params :page-size qm/default-page-size))
        page-num (Integer. (get params :page-num qm/default-page-num))
        params (dissoc params :options :page-size :page-num)]
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

