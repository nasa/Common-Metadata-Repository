(ns cmr.indexer.services.concepts.granule.attribute
  "Contains functions for converting attributes into a elastic documents"
  (:require [clj-time.format :as f]
            [cmr.umm.echo10.collection.product-specific-attribute :as coll-psa]
            [cmr.common.services.errors :as errors]))

(defmulti value->elastic-value
  "Converts a attribute value into the elastic value that should be transmitted"
  (fn [type value]
    type))

(defmethod value->elastic-value :default
  [type value]
  value)

(defmethod value->elastic-value :datetime
  [type value]
  (f/unparse (f/formatters :date-time) value))

(defmethod value->elastic-value :time
  [type value]
  ;; This relies on the fact that times are parsed into times on day 1970-01-01
  (f/unparse (f/formatters :date-time) value))

(defmethod value->elastic-value :date
  [type value]
  (f/unparse (f/formatters :date-time) value))

(def type->field-name
  "Converts an attribute type into the indexed field name"
  {:string "string-value"
   :time "time-value"
   :date "date-value"
   :datetime "datetime-value"
   :boolean "string-value"
   :time-string "string-value"
   :date-string "string-value"
   :datetime-string "string-value"
   :int "int-value"
   :float "float-value"})

(defn psa-ref->elastic-doc
  "Converts a PSA ref into the correct elastic document"
  [type psa-ref]
  (let [field-name (type->field-name type)]
    {:name (:name psa-ref)
     field-name (map (comp #(value->elastic-value type %)
                           #(coll-psa/parse-value type %))
                     (:values psa-ref))}))

(defn psa-refs->elastic-docs
  "Converts the psa-refs into a list of elastic documents"
  [collection granule]
  (let [parent-type-map (into {} (for [psa (:product-specific-attributes collection)]
                                   [(:name psa) (:data-type psa)]))]
    (map (fn [psa-ref]
           (let [type (parent-type-map (:name psa-ref))]
             (when-not type
               (errors/internal-error! (format "Could not find parent attribute [%s] in collection [%s]"
                                               (:name psa-ref)
                                               (:concept-id collection))))
             (psa-ref->elastic-doc type psa-ref)))
         (:product-specific-attributes granule))))