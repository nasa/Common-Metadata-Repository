(ns cmr.system-int-test.data2.aql-additional-attribute
  "Contains helper functions for converting additional attribute value parameters into aql string."
  (:require [clojure.data.xml :as x]
            [clj-time.core :as t]
            [cmr.common.date-time-parser :as p]
            [cmr.system-int-test.data2.aql :as a]))

(defn generate-time-element
  "Returns the xml element for the give time string"
  [value]
  (when value
    (let [dt (p/parse-time value)]
      (x/element :time {:HH (str (t/hour dt))
                        :MI (str (t/minute dt))
                        :SS (str (t/second dt))}))))

(defn generate-named-time-element
  "Returns the xml element for the given name and time value string"
  [elem-name value]
  (when value
    (x/element elem-name {}
               (generate-time-element value))))

(defn generate-time-range-value-element
  "Returns the xml element for time range value of start-time and stop-time"
  [start-time stop-time]
  (x/element "timeRange" {}
             (generate-named-time-element :startTime start-time)
             (generate-named-time-element :stopTime stop-time)))

(defmulti generate-attribute-value-element
  "Returns the additional attribute value element of the given type, value and options"
  (fn [type value ignore-case pattern]
    type))

(defmethod generate-attribute-value-element :string
  [type value ignore-case pattern]
  (a/generate-string-value-element value ignore-case pattern))

(defmethod generate-attribute-value-element :range
  [type value ignore-case pattern]
  (a/generate-range-element value))

(defmethod generate-attribute-value-element :float
  [type value ignore-case pattern]
  (x/element :float {} value))

(defmethod generate-attribute-value-element :floatRange
  [type value ignore-case pattern]
  (a/generate-range-element :floatRange value))

(defmethod generate-attribute-value-element :int
  [type value ignore-case pattern]
  (x/element :int {} value))

(defmethod generate-attribute-value-element :intRange
  [type value ignore-case pattern]
  (a/generate-range-element :intRange value))

(defmethod generate-attribute-value-element :date
  [type value ignore-case pattern]
  (a/generate-date-element value))

(defmethod generate-attribute-value-element :dateRange
  [type value ignore-case pattern]
  (apply a/generate-date-range-value-element value))

(defmethod generate-attribute-value-element :time
  [type value ignore-case pattern]
  (generate-time-element value))

(defmethod generate-attribute-value-element :timeRange
  [type value ignore-case pattern]
  (apply generate-time-range-value-element value))

(defn- generate-additional-attribute-element
  [additional-attrib]
  (let [{:keys [name type value ignore-case pattern]} additional-attrib]
    (x/element :additionalAttribute {}
               (x/element :additionalAttributeName {} name)
               (x/element :additionalAttributeValue {}
                          (generate-attribute-value-element type value ignore-case pattern)))))

(defmethod a/generate-element :additional-attributes
  [condition]
  (let [elem-key (a/condition->element-name condition)
        additional-attribs (elem-key condition)
        operator-option (a/condition->operator-option condition)]
    (x/element elem-key operator-option
               (map generate-additional-attribute-element additional-attribs))))

