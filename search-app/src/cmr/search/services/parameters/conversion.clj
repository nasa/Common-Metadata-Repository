(ns cmr.search.services.parameters.conversion
  "Contains functions for parsing and converting query parameters to query conditions"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.common.util :as u]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.common.concepts :as cc]
            [cmr.common.date-time-parser :as parser]))

(def concept-param->type
  "A mapping of param names to query condition types based on concept-type"
  {:collection {:entry-title :string
                :entry-id :string
                :provider :string
                :attribute :attribute
                :short-name :string
                :version :string
                :updated-since :updated-since
                :revision-date :revision-date
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
                :two-d-coordinate-system :two-d-coordinate-system
                :science-keywords :science-keywords
                :dif-entry-id :dif-entry-id
                :downloadable :boolean
                :browsable :boolean
                :polygon :polygon
                :bounding-box :bounding-box
                :point :point
                :keyword :keyword
                :line :line}
   :granule {:granule-ur :string
             :concept-id :granule-concept-id
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
             :revision-date :revision-date
             :temporal :temporal
             :platform :inheritance
             :instrument :inheritance
             :sensor :inheritance
             :project :string
             :cloud-cover :num-range
             :exclude :exclude
             :downloadable :boolean
             :polygon :polygon
             :bounding-box :bounding-box
             :point :point
             :line :line
             :browsable :boolean
             :two-d-coordinate-system :two-d-coordinate-system}})

(def always-case-sensitive
  "A set of parameters that will always be case sensitive"
  #{:concept-id :collection-concept-id})

(defn case-sensitive-field?
  "Return true if the given field is a case-sensitive field"
  [field options]
  (or (contains? always-case-sensitive field)
      (= "false" (get-in options [field :ignore-case]))))

(defn pattern-field?
  "Returns true if the field is a pattern"
  [field options]
  (= "true" (get-in options [field :pattern])))

(defn group-operation
  "Returns the group operation (:and or :or) for the given field."
  ([field options]
   (group-operation field options :or))
  ([field options default]
   (cond
     (= "true" (get-in options [field :and])) :and
     (= "true" (get-in options [field :or])) :or
     :else default)))

(defn- param-name->type
  "Returns the query condition type based on the given concept-type and param-name."
  [concept-type param-name]
  (get-in concept-param->type [concept-type param-name]))

(defmulti parameter->condition
  "Converts a parameter into a condition"
  (fn [concept-type param value options]
    (param-name->type concept-type param)))

(defmethod parameter->condition :default
  [concept-type param value options]
  (errors/internal-error!
    (format "Could not find parameter handler for [%s] with concept-type [%s]"
            param concept-type)))

(defn string-parameter->condition
  [param value options]
  (if (sequential? value)
    (gc/group-conds (group-operation param options)
                    (map #(string-parameter->condition param % options) value))
    (let [case-sensitive (case-sensitive-field? param options)
          pattern (pattern-field? param options)]
      (qm/string-condition param value case-sensitive pattern))))

(defmethod parameter->condition :string
  [concept-type param value options]
  (string-parameter->condition param value options))

(defmethod parameter->condition :keyword
  [concept-type param value options]
  (let [pattern (pattern-field? param options)
        keywords (str/lower-case value)]
    (qm/text-condition :keyword keywords)))

;; Special case handler for concept-id. Concept id can refer to a granule or collection.
;; If it's a granule query with a collection concept id then we convert the parameter to :collection-concept-id
(defmethod parameter->condition :granule-concept-id
  [concept-type param value options]
  (let [values (if (sequential? value) value [value])
        {granule-concept-ids :granule
         collection-concept-ids :collection} (group-by (comp :concept-type cc/parse-concept-id) values)
        collection-cond (when (seq collection-concept-ids)
                          (string-parameter->condition :collection-concept-id collection-concept-ids {}))
        granule-cond (when (seq granule-concept-ids)
                       (string-parameter->condition :concept-id granule-concept-ids options))]
    (if (and collection-cond granule-cond)
      (gc/and-conds [collection-cond granule-cond])
      (or collection-cond granule-cond))))

;; Construct an inheritance query condition for granules.
;; This will find granules which either have explicitly specified a value
;; or have not specified any value for the field and inherit it from their parent collection.
(defmethod parameter->condition :inheritance
  [concept-type param value options]
  (let [field-condition (parameter->condition :collection param value options)]
    (gc/or-conds
      [field-condition
       (gc/and-conds
         [(qm/->CollectionQueryCondition field-condition)
          (qm/map->MissingCondition {:field param})])])))

;; or-conds --> "not (CondA and CondB)" == "(not CondA) or (not CondB)"
(defmethod parameter->condition :exclude
  [concept-type param value options]
  (gc/or-conds
    (map (fn [[exclude-param exclude-val]]
           (qm/map->NegatedCondition
             {:condition (parameter->condition concept-type exclude-param exclude-val options)}))
         value)))

(defmethod parameter->condition :updated-since
  [concept-type param value options]
  (qm/map->DateRangeCondition
    {:field param
     :start-date (parser/parse-datetime
                   (if (sequential? value) (first value) value))
     :end-date nil}))

(defmethod parameter->condition :revision-date
  [concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [:revision-date :and]))
      (gc/and-conds
        (map #(parameter->condition concept-type param % options) value))
      (gc/or-conds
        (map #(parameter->condition concept-type param % options) value)))
    (let [[start-date end-date] (map str/trim (str/split value #","))]
      (qm/map->DateRangeCondition
        {:field param
         :start-date (when-not (str/blank? start-date) (parser/parse-datetime start-date))
         :end-date (when-not (str/blank? end-date) (parser/parse-datetime end-date))}))))

(defmethod parameter->condition :boolean
  [concept-type param value options]
  (cond
    (or (= "true" value) (= "false" value))
    (qm/map->BooleanCondition {:field param
                               :value (= "true" value)})

    (= "unset" (str/lower-case value))
    qm/match-all

    :else
    (errors/internal-error! (format "Boolean condition for %s has invalid value of [%s]" param value))))

(defmethod parameter->condition :readable-granule-name
  [concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (gc/and-conds
        (map #(parameter->condition concept-type param % options) value))
      (gc/or-conds
        (map #(parameter->condition concept-type param % options) value)))
    (let [case-sensitive (case-sensitive-field? :readable-granule-name options)
          pattern (pattern-field? :readable-granule-name options)]
      (gc/or-conds
        [(qm/string-condition :granule-ur value case-sensitive pattern)
         (qm/string-condition :producer-granule-id value case-sensitive pattern)]))))

(defn- collection-data-type-matches-science-quality?
  "Convert the collection-data-type parameter with wildcards to a regex. This function
  does not fully handle escaping special characters and is only intended for handling
  the special case of SCIENCE_QUALITY."
  [param case-sensitive]
  (let [param (if case-sensitive
                (str/upper-case param)
                param)
        pattern (-> param
                    (str/replace #"\?" ".")
                    (str/replace #"\*" ".*")
                    re-pattern)]
    (re-find pattern "SCIENCE_QUALITY")))

(defmethod parameter->condition :collection-data-type
  [concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (gc/and-conds
        (map #(parameter->condition concept-type param % options) value))
      (gc/or-conds
        (map #(parameter->condition concept-type param % options) value)))
    (let [case-sensitive (case-sensitive-field? :collection-data-type options)
          pattern (pattern-field? :collection-data-type options)]
      (if (or (= "SCIENCE_QUALITY" value)
              (and (= "SCIENCE_QUALITY" (str/upper-case value))
                   (not= "false" (get-in options [:collection-data-type :ignore-case])))
              (and pattern
                   (collection-data-type-matches-science-quality? value case-sensitive)))
        ; SCIENCE_QUALITY collection-data-type should match concepts with SCIENCE_QUALITY
        ; or the ones missing collection-data-type field
        (gc/or-conds
          [(qm/string-condition :collection-data-type value case-sensitive pattern)
           (qm/map->MissingCondition {:field :collection-data-type})])
        (qm/string-condition :collection-data-type value case-sensitive pattern)))))

(defmethod parameter->condition :num-range
  [concept-type param value options]
  (qm/numeric-range-str->condition param value))

;; dif-entry-id matches on entry-id or associated-difs
(defmethod parameter->condition :dif-entry-id
  [concept-type param value options]
  (gc/or-conds
    [(parameter->condition concept-type :entry-id value (set/rename-keys options {:dif-entry-id :entry-id}))
     (string-parameter->condition :associated-difs value (set/rename-keys options {:dif-entry-id :associated-difs}))]))

(defn parse-sort-key
  "Parses the sort key param and returns a sequence of maps with fields and order.
  Returns nil if no sort key was specified."
  [sort-key]
  (when sort-key
    (if (sequential? sort-key)
      (mapcat parse-sort-key sort-key)
      (let [[_ dir-char field] (re-find #"([\-+])?(.*)" sort-key)
            direction (cond
                        (= dir-char "-")
                        :desc

                        (= dir-char "+")
                        :asc

                        :else
                        ;; score sorts default to descending sort, everything else ascending
                        (if (= "score" (str/lower-case sort-key))
                          :desc
                          :asc))
            field (keyword field)]
        [{:order direction
          :field (or (lp/param-aliases field)
                     field)}]))))

(defn standard-params->query-attribs
  "Extracts standard parameters that work on any query api like page-size and page-num and returns
  them in a map as query attributes"
  [concept-type params]
  (let [page-size (Integer. (get params :page-size qm/default-page-size))
        page-num (Integer. (get params :page-num qm/default-page-num))
        ;; If there is no sort key specified and keyword parameter exists then we default to
        ;; sorting by document relevance score
        sort-keys (or (parse-sort-key (:sort-key params))
                      (when (:keyword params) [{:order :desc :field :score}]))
        echo-compatible? (= "true" (:echo-compatible params))
        hierarchical-facets? (= "true" (:hierarchical-facets params))
        result-features (concat (when (= (:include-granule-counts params) "true")
                                  [:granule-counts])
                                (when (= (:include-has-granules params) "true")
                                  [:has-granules])
                                (when (= (:include-facets params) "true")
                                  (if hierarchical-facets?
                                    [:hierarchical-facets]
                                    [:facets])))]
    {:concept-type concept-type
     :page-size page-size
     :page-num page-num
     :sort-keys sort-keys
     :result-format (:result-format params)
     :result-features (seq result-features)
     :echo-compatible? echo-compatible?}))

(defn parse-parameter-query
  "Converts parameters into a query model."
  [concept-type params]
  (let [options (u/map-keys->kebab-case (get params :options {}))
        query-attribs (standard-params->query-attribs concept-type params)
        keywords (when (:keyword params)
                   (str/split (str/lower-case (:keyword params)) #" "))
        params (if keywords (assoc params :keyword (str/join " " keywords)) params)
        params (dissoc params :options :page-size :page-num :sort-key :result-format
                       :include-granule-counts :include-has-granules :include-facets
                       :echo-compatible :hierarchical-facets)]
    (if (empty? params)
      ;; matches everything
      (qm/query query-attribs)
      ;; Convert params into conditions
      (let [conditions (map (fn [[param value]]
                              (parameter->condition concept-type param value options))
                            params)]
        (qm/query (assoc query-attribs
                         :condition (gc/and-conds conditions)
                         :keywords keywords))))))

(defn timeline-parameters->query
  "Converts parameters from a granule timeline request into a query."
  [params]
  (let [{:keys [interval start-date end-date]} params
        query (parse-parameter-query
                :granule
                (dissoc params :interval :start-date :end-date))]
    ;; Add timeline request fields to the query so that they can be used later
    ;; for processing the timeline results.
    (assoc query
           :interval (keyword interval)
           :start-date (parser/parse-datetime start-date)
           :end-date (parser/parse-datetime end-date)
           ;; Indicate the result feature of timeline so that we can preprocess
           ;; the query and add aggregations and make other changes.
           :result-features [:timeline]
           :result-format :timeline)))