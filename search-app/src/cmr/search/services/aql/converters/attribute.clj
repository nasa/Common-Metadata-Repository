(ns cmr.search.services.aql.converters.attribute
  "Contains functions for parsing, validating and converting additionalAttributes aql element to query conditions"
  (:require [clojure.string :as s]
            [cmr.common.xml :as cx]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.parameters.converters.attribute :as p]
            [cmr.search.services.messages.attribute-messages :as msg]
            [cmr.common.date-time-parser :as date-time-parser]
            [cmr.common.services.errors :as errors]))

(defn- attrib-value->condition
  [attrib-type attrib-name value]
  (let [condition (qm/map->AttributeValueCondition
                    {:type attrib-type
                     :name attrib-name
                     :value value})]
    (p/parse-component-type condition false)))

(defn- attrib-range-elem->condition
  [attrib-type attrib-name range-elem]
  (let [minv (a/remove-outer-single-quotes (get-in range-elem [:attrs :lower]))
        maxv (a/remove-outer-single-quotes (get-in range-elem [:attrs :upper]))
        condition (qm/map->AttributeRangeCondition
                    {:type attrib-type
                     :name attrib-name
                     :min-value (when-not (s/blank? minv) minv)
                     :max-value (when-not (s/blank? maxv) maxv)})]
    (p/parse-component-type condition false)))

(defn- time-from-strings
  "Returns time from strings of hour, minute and second. Returns nil if all strings are nil."
  [hour minute sec]
  (when (or hour minute sec)
    (let [time-str (format "%s:%s:%s" hour minute sec)]
      (date-time-parser/parse-time time-str))))

(defn- parse-time-range-element
  "Returns start-time and stop-time in a vector by parsing the given time range element"
  [element]
  (let [{hour :HH minute :MI sec :SS} (cx/attrs-at-path element [:startTime :time])
        start-time (time-from-strings hour minute sec)
        {hour :HH minute :MI sec :SS} (cx/attrs-at-path element [:stopTime :time])
        stop-time (time-from-strings hour minute sec)]
    [start-time stop-time]))

(defmulti attrib-value-element->condition
  "Returns the query condition of the given additional attribute value element"
  (fn [attrib-name value-elem]
    (:tag value-elem)))

(defmethod attrib-value-element->condition :value
  [attrib-name value-elem]
  (attrib-value->condition :string attrib-name (a/element->string-content value-elem)))

(defmethod attrib-value-element->condition :textPattern
  [attrib-name value-elem]
  (let [value (-> value-elem a/element->string-content a/aql-pattern->cmr-pattern)]
    (qm/map->AttributeValueCondition
      {:type :string
       :name attrib-name
       :value value
       :pattern? true})))

(defmethod attrib-value-element->condition :list
  [attrib-name value-elem]
  (let [values (cx/strings-at-path value-elem [:value])
        conditions (map (partial attrib-value->condition :string attrib-name) values)]
    (gc/or-conds conditions)))

(defmethod attrib-value-element->condition :range
  [attrib-name value-elem]
  (attrib-range-elem->condition :string attrib-name value-elem))

(defmethod attrib-value-element->condition :float
  [attrib-name value-elem]
  (attrib-value->condition :float attrib-name (a/element->string-content value-elem)))

(defmethod attrib-value-element->condition :floatRange
  [attrib-name value-elem]
  (attrib-range-elem->condition :float attrib-name value-elem))

(defmethod attrib-value-element->condition :int
  [attrib-name value-elem]
  (attrib-value->condition :int attrib-name (a/element->string-content value-elem)))

(defmethod attrib-value-element->condition :intRange
  [attrib-name value-elem]
  (attrib-range-elem->condition :int attrib-name value-elem))

(defmethod attrib-value-element->condition :Date
  [attrib-name value-elem]
  (let [{year :YYYY month :MM day :DD hour :HH minute :MI sec :SS} (:attrs value-elem)
        value (a/date-time-from-strings year month day hour minute sec)]
    ;; There is no Datetime element for AdditionalAttribute in AQL,
    ;; the Date element covers both the date type and datetime type of AA.
    ;; This is different from parameter search of AA.
    (gc/or-conds
      [(qm/map->AttributeValueCondition
         {:type :datetime
          :name attrib-name
          :value value})
       (qm/map->AttributeValueCondition
         {:type :date
          :name attrib-name
          :value value})])))

(defmethod attrib-value-element->condition :dateRange
  [attrib-name value-elem]
  (let [[start-date stop-date] (a/parse-date-range-element value-elem)]
    ;; There is no datetimeRange element for AdditionalAttribute in AQL,
    ;; the dateRange element covers both the date type and datetime type of AA.
    ;; This is different from parameter search of AA.
    (if (or start-date stop-date)
      (gc/or-conds
        [(qm/map->AttributeRangeCondition
           {:type :datetime
            :name attrib-name
            :min-value start-date
            :max-value stop-date})
         (qm/map->AttributeRangeCondition
           {:type :date
            :name attrib-name
            :min-value start-date
            :max-value stop-date})])
      {:errors [(msg/one-of-min-max-msg)]})))

(defmethod attrib-value-element->condition :time
  [attrib-name value-elem]
  (let [{hour :HH minute :MI sec :SS} (:attrs value-elem)
        value (format "%s:%s:%s" hour minute sec)]
    (attrib-value->condition :time attrib-name value)))

(defmethod attrib-value-element->condition :timeRange
  [attrib-name value-elem]
  (let [[start-time stop-time] (parse-time-range-element value-elem)]
    (if (or start-time stop-time)
      (qm/map->AttributeRangeCondition
        {:type :time
         :name attrib-name
         :min-value start-time
         :max-value stop-time})
      {:errors [(msg/one-of-min-max-msg)]})))

(defn- additional-attribute-element->conditions
  "Returns the query conditions of the given additionalAttribute element"
  [additional-attribute]
  (let [attrib-name (a/remove-outer-single-quotes (cx/string-at-path additional-attribute [:additionalAttributeName]))
        attrib-value (first (cx/content-at-path additional-attribute [:additionalAttributeValue]))]
    (attrib-value-element->condition attrib-name attrib-value)))

(defn- raise-condition-errors
  "Raise condition errors if there are validation errors in the conditions"
  [conditions]
  (let [validation-errors (mapcat :errors conditions)]
    (when (seq validation-errors)
      (errors/throw-service-errors :bad-request validation-errors))))

;; Converts additionalAttributes element into query condition, returns the converted condition
(defmethod a/element->condition :attribute
  [concept-type element]
  (let [attributes (cx/elements-at-path element [:additionalAttribute])
        operator (get-in element [:attrs :operator])
        conditions (map additional-attribute-element->conditions attributes)
        attrib-condition (if (= "OR" operator)
                           (gc/or-conds conditions)
                           (gc/and-conds conditions))]
    (raise-condition-errors conditions)
    (if (= :granule concept-type)
      ;; Granule attribute queries will inherit values from their parent collections.
      (gc/or-conds [attrib-condition (qm/->CollectionQueryCondition attrib-condition)])
      attrib-condition)))