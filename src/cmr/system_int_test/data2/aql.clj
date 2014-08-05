(ns cmr.system-int-test.data2.aql
  "Contains helper functions for converting parameters into aql string."
  (:require [clojure.string :as s]
            [clojure.data.xml :as x]))

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

(defn- generate-element
  "Returns the xml element for the given element condition"
  [condition]
  (let [elem-key (first (remove #{:ignore-case :pattern} (keys condition)))
        elem-value (elem-key condition)
        {:keys [ignore-case pattern]} condition]
    (x/element elem-key {}
               (if (and (sequential? elem-value) (> (count elem-value) 1))
                 ;; a list with more than one value
                 (if pattern
                   (x/element :patternList {}
                              (map (partial generate-value-element ignore-case pattern) elem-value))
                   (x/element :list {}
                              (map (partial generate-value-element ignore-case pattern) elem-value)))
                 ;; a single value
                 (let [value (if (sequential? elem-value) (first elem-value) elem-value)]
                   (generate-value-element ignore-case pattern value))))))

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

