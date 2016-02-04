(ns cmr.indexer.data.concepts.attribute
  "Contains functions for converting attributes into a elastic documents"
  (:require [clj-time.format :as f]
            [clojure.string :as str]
            [cmr.umm.collection.product-specific-attribute :as coll-psa]
            [cmr.common.services.errors :as errors]))

(defmulti value->elastic-value
  "Converts a attribute value into the elastic value that should be transmitted"
  (fn [type value]
    type))

(defmethod value->elastic-value :default
  [type value]
  (or value ""))

(defmethod value->elastic-value :boolean
  [type value]
  (if (nil? value) "" value))

(defmethod value->elastic-value :datetime
  [type value]
  (when value (f/unparse (f/formatters :date-time) value)))

(defmethod value->elastic-value :time
  [type value]
  ;; This relies on the fact that times are parsed into times on day 1970-01-01
  (when value (f/unparse (f/formatters :date-time) value)))

(defmethod value->elastic-value :date
  [type value]
  (when value (f/unparse (f/formatters :date-time) value)))

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

(defn psa-ref->nested-docs
  "Converts a PSA ref into the nested documents going in an elastic document"
  [type psa-ref]
  (let [field-name (type->field-name type)]
    (if (some #{type} [:string :boolean :time-string :date-string :datetime-string])
      [{:name (:name psa-ref)
        field-name (map (comp #(value->elastic-value type %)
                              #(coll-psa/parse-value type %))
                        (:values psa-ref))}
       {:name (:name psa-ref)
        (str field-name ".lowercase") (map (comp str/lower-case
                                                 #(value->elastic-value type %)
                                                 #(coll-psa/parse-value type %))
                                           (:values psa-ref))}]
      [{:name (:name psa-ref)
        field-name (map (comp #(value->elastic-value type %)
                              #(coll-psa/parse-value type %))
                        (:values psa-ref))}])))

(defn psa-refs->elastic-docs
  "Converts the psa-refs into a list of elastic documents"
  [collection granule]
  (let [parent-type-map (into {} (for [psa (:product-specific-attributes collection)]
                                   [(:name psa) (:data-type psa)]))]
    (mapcat (fn [psa-ref]
              (let [type (parent-type-map (:name psa-ref))]
                (when-not type
                  (errors/internal-error!
                   (format "Could not find parent attribute [%s] in collection [%s] for granule [%s]"
                           (:name psa-ref)
                           (:concept-id collection)
                           (:concept-id granule))))
                (psa-ref->nested-docs type psa-ref)))
            (:product-specific-attributes granule))))

(defn psa->nested-docs
  "Converts a PSA into the portion going in an elastic document"
  [psa]
  (let [{:keys [name group data-type parsed-value]} psa
        field-name (type->field-name data-type)
        psa-map {:name name :group group}]
    (if (some #{data-type} [:string :boolean :time-string :date-string :datetime-string])
      [(assoc psa-map field-name (value->elastic-value data-type parsed-value))
       (assoc psa-map
              (str field-name ".lowercase")
              (str/lower-case (value->elastic-value data-type parsed-value)))]
      [(assoc psa-map field-name (value->elastic-value data-type parsed-value))])))

(defn psas->elastic-docs
  "Converts the psa into a list of elastic documents"
  [collection]
  (mapcat psa->nested-docs (:product-specific-attributes collection)))

(defn psa->keywords
  "Converts a PSA into a vector of terms to be used in keyword searches"
  [psa]
  (let [{:keys [name data-type parsed-value description]} psa
        parsed-value (when parsed-value (data-type value->elastic-value parsed-value))]
    (filter identity [name parsed-value description])))

(defn psas->keywords
  [collection]
  "Converts the psas into a vector to be used in keyword searches"
  (mapcat psa->keywords (:product-specific-attributes collection)))


