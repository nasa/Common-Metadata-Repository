(ns cmr.search.services.parameters
  "Contains functions for parsing and converting query parameters to query conditions"
  (:require [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.common.date-time-parser :as dt-parser]
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
                :updated-since :updated-since
                :processing-level-id :string
                :temporal :temporal
                :concept-id :string
                :platform :string
                :instrument :string
                :sensor :string
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
             :orbit-number :orbit-number
             :equator-crossing-longitude :equator-crossing-longitude
             :equator-crossing-date :equator-crossing-date
             :version :collection-query
             :updated-since :updated-since
             :temporal :temporal
             :platform :inheritance
             :instrument :inheritance
             :sensor :inheritance
             :project :string
             :cloud-cover :num-range
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

;; Construct an inheritance query condition for granules.
;; This will find granules which either have explicitly specified a value
;; or have not specified any value for the field and inherit it from their parent collection.
(defmethod parameter->condition :inheritance
  [concept-type param value options]
  (let [field-condition (parameter->condition :collection param value options)]
    (qm/or-conds
      [field-condition
       (qm/and-conds
         [(qm/->CollectionQueryCondition field-condition)
          (qm/map->MissingCondition {:field (q2e/query-field->elastic-field param concept-type)})])])))

(defmethod parameter->condition :updated-since
  [concept-type param value options]
  (qm/map->DateRangeCondition
    {:field param
     :start-date (dt-parser/parse-datetime
                   (if (sequential? value) (first value) value))
     :end-date nil}))

(defmethod parameter->condition :boolean
  [concept-type param value options]
  (if (or (= "true" value) (= "false" value))
    (qm/map->BooleanCondition {:field param
                               :value (= "true" value)})
    (errors/internal-error! (format "Boolean condition for %s has invalid value of [%s]" param value))))


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


(defmethod parameter->condition :num-range
  [concept-type param value options]
  (qm/numeric-range-condition param value))

(defn parse-sort-key
  "Parses the sort key param and returns a sequence of maps with fields and order.
  Returns nil if no sort key was specified."
  [sort-key]
  (when sort-key
    (if (sequential? sort-key)
      (mapcat parse-sort-key sort-key)
      (let [[_ dir-char field] (re-find #"([\-+])?(.*)" sort-key)
            direction (case dir-char
                        "-" :desc
                        "+" :asc
                        :asc)
            field (keyword field)]
        [{:order direction
          :field (or (param-aliases field)
                     field)}]))))

;; Changes lagacy map range condtions in the param[minValue]/param[maxValue] format
;; to the cmr format: min,max.
(defn- proces-legacy-range-maps
  [concept-type params]
  (reduce-kv (fn [memo k v]
               ;; look for parameters in the map form
               (if (map? v)
                 (let [{:keys [value min-value max-value]} v]
                   (if (or value min-value max-value)
                     ;; convert the map into a comma separated string
                     (if value
                       (assoc memo k value)
                       (assoc memo k (str min-value "," max-value)))
                     memo))
                 memo))
             params
             params))


(defn- process-equator-crossing-date
  [concept-type params]
  (let [{:keys [equator-crossing-start-date equator-crossing-end-date]} params]
    (if (or equator-crossing-start-date equator-crossing-end-date)
      (-> params
          (dissoc :equator-crossing-start-date :equator-crossing-end-date)
          (assoc :equator-crossing-date (str equator-crossing-start-date
                                             ","
                                             equator-crossing-end-date)))
      params)))

;; Add others to this list as needed - note that order is important here
(def legacy-multi-params-condition-funcs
  [process-equator-crossing-date
   proces-legacy-range-maps])

(defn process-legacy-multi-params-conditions
  "Handle conditions that use a legacy range style of using two parameters to specify a range."
  [concept-type params]
  (reduce #(%2 concept-type %1)
          params
          legacy-multi-params-condition-funcs))

(defn parameters->query
  "Converts parameters into a query model."
  [concept-type params]
  (let [options (u/map-keys->kebab-case (get params :options {}))
        page-size (Integer. (get params :page-size qm/default-page-size))
        page-num (Integer. (get params :page-num qm/default-page-num))
        sort-keys (parse-sort-key (:sort-key params))
        params (dissoc params :options :page-size :page-num :sort-key)]
    (if (empty? params)
      ;; matches everything
      (qm/query {:concept-type concept-type
                 :page-size page-size
                 :page-num page-num
                 :sort-keys sort-keys})
      ;; Convert params into conditions
      (let [conditions (map (fn [[param value]]
                              (parameter->condition concept-type param value options))
                            params)]
        (qm/query {:concept-type concept-type
                   :page-size page-size
                   :page-num page-num
                   :condition (qm/and-conds conditions)
                   :sort-keys sort-keys})))))

