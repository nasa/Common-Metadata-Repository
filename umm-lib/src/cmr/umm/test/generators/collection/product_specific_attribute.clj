(ns cmr.umm.test.generators.collection.product-specific-attribute
  "Provides clojure.test.check generators for product specific attributes"
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(def names
  (ext-gen/string-alpha-numeric 1 10))

(def descriptions
  (ext-gen/string-alpha-numeric 1 10))

(def data-types
  (gen/elements c/product-specific-attribute-types))

(def string-values
  (ext-gen/string-alpha-numeric 1 10))

(def float-values
  (gen/fmap double gen/ratio))

(def int-values
  gen/int)

(def boolean-values
  gen/boolean)

(def date-values
  "Generates date values without a time component"
  (gen/fmap
    (fn [t]
      (t/date-time (t/year t) (t/month t) (t/day t)))
    ext-gen/date-time))

(def time-values
  "Generates time values without a date component"
  (gen/fmap
    (fn [t]
      (t/date-time 1970 1 1 (t/hour t) (t/minute t) (t/second t) (t/milli t)))
    ext-gen/date-time))

(def datetime-values
  ext-gen/date-time)

(defmulti data-type->value-gen
  "Converts the given data type into a generator for a value that matches the given type"
  (fn [data-type]
    data-type))

(defmethod data-type->value-gen :string
  [data-type]
  string-values)

(defmethod data-type->value-gen :float
  [data-type]
  float-values)

(defmethod data-type->value-gen :int
  [data-type]
  int-values)

(defmethod data-type->value-gen :boolean
  [data-type]
  boolean-values)

(defmethod data-type->value-gen :date
  [data-type]
  date-values)

(defmethod data-type->value-gen :time
  [data-type]
  time-values)

(defmethod data-type->value-gen :datetime
  [data-type]
  datetime-values)

(defmethod data-type->value-gen :date-string
  [data-type]
  (gen/fmap #(f/unparse (f/formatters :year-month-day) %)
            date-values))

(defmethod data-type->value-gen :time-string
  [data-type]
  (gen/fmap #(f/unparse (f/formatters :hour-minute-second-fraction) %)
            time-values))

(defmethod data-type->value-gen :datetime-string
  [data-type]
  (gen/fmap str datetime-values))

(defmulti data-type-allows-range?
  "Returns true if the data type allows parameter range begin and end."
  (fn [data-type]
    data-type))

(defmethod data-type-allows-range? :default
  [data-type]
  true)

(defmethod data-type-allows-range? :boolean
  [data-type]
  false)

(def data-type-with-values
  "A generator of data types with 3 values for parameter range begin, a value, and end matching the
  given data type"
  (gen/bind
    data-types
    (fn [data-type]
      (gen/hash-map :data-type (gen/return data-type)
                    :values (gen/fmap sort (gen/vector (data-type->value-gen data-type) 3))))))

(def product-specific-attributes
  (gen/fmap
    (fn [[name description {:keys [data-type values]} include-begin? include-end?]]
      (let [[begin value end] values
            attribs {:name name
                     :description description
                     :data-type data-type
                     :parsed-value value
                     :value (psa/gen-value data-type value)}
            include-begin? (and (data-type-allows-range? data-type) include-begin?)
            include-end? (and (data-type-allows-range? data-type) include-end?)
            attribs (if include-begin?
                      (assoc attribs
                             :parsed-parameter-range-begin begin
                             :parameter-range-begin (psa/gen-value data-type begin))
                      attribs)
            attribs (if include-end?
                      (assoc attribs
                             :parsed-parameter-range-end end
                             :parameter-range-end (psa/gen-value data-type end))
                      attribs)]
        (c/map->ProductSpecificAttribute attribs)))
    (gen/tuple names descriptions data-type-with-values gen/boolean gen/boolean)))

