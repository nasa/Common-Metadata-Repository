(ns cmr.system-int-test.data2.aql
  "Contains helper functions for converting parameters into aql string."
  (:require [clojure.string :as s]
            [clojure.data.xml :as x]
            [clj-time.core :as t]
            [cmr.common.date-time-parser :as p]))

(defn- generate-value-element
  "Returns the xml element for the given element value. It will be either value or textPattern."
  [ignore-case pattern value]
  (let [case-option (case ignore-case
                      true {:caseInsensitive "Y"}
                      false {:caseInsensitive "N"}
                      {})]
    (if pattern
      (x/element :textPattern case-option value)
      (x/element :value case-option value))))

(defn- generate-attr-value-element
  "Returns the attribute value xml element for the given name and value string"
  [elem-name value]
  (when value (x/element elem-name {:value (str value)})))

(defn- generate-date-element
  "Returns the xml element for the given name and value string"
  [elem-name value]
  (when value
    (let [dt (p/parse-datetime value)]
      (x/element elem-name {}
                 (x/element :Date {:YYYY (str (t/year dt))
                                   :MM (str (t/month dt))
                                   :DD (str (t/day dt))
                                   :HH (str (t/hour dt))
                                   :MI (str (t/minute dt))
                                   :SS (str (t/second dt))})))))

(def element-key-type-mapping
  "A mapping of AQL element key to its type, only keys with a type other than string are listed."
  {:onlineOnly :boolean
   :temporal :temporal})

(defn- condition->element-type
  "Returns the element type of the condition"
  [condition]
  (let [elem-key (first (remove #{:ignore-case :pattern} (keys condition)))]
    (get element-key-type-mapping elem-key :string)))

(defmulti generate-element
  "Returns the xml element for the given element condition"
  (fn [condition]
    (condition->element-type condition)))

(defmethod generate-element :string
  [condition]
  (let [elem-key (first (remove #{:ignore-case :pattern} (keys condition)))
        elem-value (elem-key condition)
        {:keys [ignore-case pattern]} condition]
    (x/element elem-key {}
               (if (sequential? elem-value)
                 ;; a list with at least one value
                 (if pattern
                   (x/element :patternList {}
                              (map (partial generate-value-element ignore-case pattern) elem-value))
                   (x/element :list {}
                              (map (partial generate-value-element ignore-case pattern) elem-value)))
                 ;; a single value
                 (let [value (if (sequential? elem-value) (first elem-value) elem-value)]
                   (generate-value-element ignore-case pattern value))))))

(defmethod generate-element :boolean
  [condition]
  (let [elem-key (first (remove #{:ignore-case :pattern} (keys condition)))
        elem-value (elem-key condition)]
    (case elem-value
      true (x/element elem-key {:value "Y"})
      nil (x/element elem-key {})
      nil)))

(defmethod generate-element :temporal
  [condition]
  (let [elem-key (first (remove #{:ignore-case :pattern} (keys condition)))
        {:keys [start-date stop-date start-day end-day]} (elem-key condition)]
    (x/element :temporal {}
               (generate-date-element :startDate start-date)
               (generate-date-element :stopDate stop-date)
               (generate-attr-value-element :startDay start-day)
               (generate-attr-value-element :endDay end-day))))

(defn- generate-data-center
  "Returns the dataCenter element for the data center condition"
  [condition]
  (if (empty? (:dataCenterId condition))
    (x/element :dataCenterId {}
               (x/element :all {}))
    (generate-element condition)))

(defn generate-aql
  "Returns aql search string from input data-center-condition and conditions.
  data-center-condition is either nil or a map with possible keys of :dataCenter :ignore-case and :pattern,
  conditions is a vector of conditions that will populate the where conditions."
  [concept-type data-center-condition conditions]
  (let [condition-elem-name (if (= :collection concept-type) :collectionCondition :granuleCondition)]
    (x/emit-str
      (x/element :query {}
                 (x/element :for {:value (format "%ss" (name concept-type))})
                 (generate-data-center data-center-condition)
                 (x/element :where {}
                            (x/element condition-elem-name {}
                                       (map generate-element conditions)))))))

