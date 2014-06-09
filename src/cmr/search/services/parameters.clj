(ns cmr.search.services.parameters
  "Contains functions for parsing and converting query parameters to query conditions"
  (:require [clojure.string :as s]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.common.date-time-parser :as dt-parser]
            [cmr.common.util :as u]
            [cmr.search.services.legacy-parameters :as lp]))

(def concept-param->type
  "A mapping of param names to query condition types based on concept-type"
  {:collection {:entry-title :string
                :entry-id :string
                :provider :string
                :short-name :string
                :version :string
                :updated-since :updated-since
                :processing-level-id :string
                :collection-data-type :collection-data-type
                :temporal :temporal
                :concept-id :string
                :platform :string
                :instrument :string
                :sensor :string
                :project :string
                :archive-center :string
                :spatial-keyword :string
                :two-d-coordinate-system-name :string
                :science-keywords :science-keywords}
   :granule {:granule-ur :string
             :collection-concept-id :string
             :producer-granule-id :string
             :day-night :string
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
             :exclude :exclude
             :downloadable :boolean
             :polygon :polygon}})

(defn- case-sensitive-field?
  "Return true if the given field is a case-sensitive field"
  [field]
  (some #{:concept-id :collection-concept-id} [field]))

(defn string-condition-with-options
  "Returns a string condition with the given field, value and options"
  [field value options options-field]
  (let [case-sensitive (= "false" (get-in options [options-field :ignore-case]))
        case-sensitive (if (case-sensitive-field? field) true case-sensitive)]
    (qm/string-condition field
                         value
                         case-sensitive
                         (= "true" (get-in options [options-field :pattern])))))

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
    (string-condition-with-options param value options param)))

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

;; or-conds --> "not (CondA and CondB)" == "(not CondA) or (not CondB)"
(defmethod parameter->condition :exclude
  [concept-type param value options]
  (qm/or-conds
    (map (fn [[exclude-param exclude-val]]
           (qm/map->NegatedCondition
             {:condition (parameter->condition concept-type exclude-param exclude-val options)}))
         value)))

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
      [(string-condition-with-options :granule-ur value options :readable-granule-name)
       (string-condition-with-options :producer-granule-id value options :readable-granule-name)])))

(defmethod parameter->condition :collection-data-type
  [concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (qm/and-conds
        (map #(parameter->condition concept-type param % options) value))
      (qm/or-conds
        (map #(parameter->condition concept-type param % options) value)))
    (let [nrt-aliases ["near_real_time","nrt", "NRT", "near real time","near-real time","near-real-time","near real-time"]
          value (if (some #{value} nrt-aliases) "NEAR_REAL_TIME" value)]
      (if (or (= "SCIENCE_QUALITY" value)
              (and (= "SCIENCE_QUALITY" (s/upper-case value))
                   (not= "false" (get-in options [:collection-data-type :ignore-case]))))
        (qm/or-conds
          [(string-condition-with-options :collection-data-type value options :collection-data-type)
           (qm/map->MissingCondition {:field :collection-data-type})])
        (string-condition-with-options :collection-data-type value options :collection-data-type)))))

(defmethod parameter->condition :num-range
  [concept-type param value options]
  (qm/numeric-range-str->condition param value))

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
          :field (or (lp/param-aliases field)
                     field)}]))))

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

