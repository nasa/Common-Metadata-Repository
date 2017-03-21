(ns cmr.indexer.data.concepts.attribute
  "Contains functions for converting attributes into a elastic documents"
  (:require
    [camel-snake-kebab.core :as csk]
    [clj-time.format :as f]
    [clojure.string :as str]
    [cmr.common.services.errors :as errors]
    [cmr.umm-spec.additional-attribute :as aa]
    [cmr.umm.collection.product-specific-attribute :as coll-psa]))

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
  (let [parent-type-map (into {} (for [psa (:AdditionalAttributes collection)]
                                   [(:Name psa) (csk/->kebab-case-keyword (:DataType psa))]))]
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

(defn- aa->nested-docs
  "Converts an AdditionalAttribute into the portion going in an elastic document"
  [aa]
  (let [{Group :Group Name :Name DataType :DataType parsed-value ::aa/parsed-value}
        (aa/attribute-with-parsed-value aa)
        data-type (csk/->kebab-case-keyword DataType)
        field-name (type->field-name data-type)
        aa-map {:name Name :group Group}]
    (if (some #{data-type} [:string :boolean :time-string :date-string :datetime-string])
      [(assoc aa-map field-name (value->elastic-value data-type parsed-value))
       (assoc aa-map
              (str field-name ".lowercase")
              (str/lower-case (value->elastic-value data-type parsed-value)))]
      [(assoc aa-map field-name (value->elastic-value data-type parsed-value))])))

(defn aas->elastic-docs
  "Converts the AdditionalAttributes into a list of elastic documents"
  [collection]
  (mapcat aa->nested-docs (:AdditionalAttributes collection)))

(defn psa->keywords
  "Converts a PSA into a vector of terms to be used in keyword searches"
  [psa]
  (let [{:keys [name data-type parsed-value description]} psa
        parsed-value (when parsed-value (data-type value->elastic-value parsed-value))]
    (filter identity [name parsed-value description])))
