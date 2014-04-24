(ns cmr.search.services.parameter-converters.attribute
    "Contains functions for converting additional attribute search parameters to a query model"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]
            [clojure.string :as str]))

(defn empty->nil
  "Converts an empty string to nil"
  [s]
  (when-not (empty? s)
    s))

(defn invalid-num-parts-msg
  []
  (str "Invalid number of additional attribute parts. "
       "Format is \"type,name,value\" or \"type,name,min,max\"."))

(defn invalid-type-msg
  [type]
  (format "[%s] is an invalid type" (str type)))

(defn invalid-value-msg
  [type value]
  (format "[%s] is an invalid value for type [%s]" (str value) (name type)))

(defn one-of-min-max-msg
  []
  "At least one of min or max must be provided for an additional attribute search.")

(defn value->components
  "Parses an additional attribute value into it's constituent parts"
  [value]
  (let [comma-escape "\\,"
        comma-replace "%COMMA%" ; used to replace escaped commas during splitting
        parts (-> value
                  (str/replace comma-escape comma-replace)
                  (str/split #"," 5))
        parts (map #(str/replace % comma-replace ",") parts)
        parts (map empty->nil parts)]

    (case (count parts)
      3
      (let [[t n v] parts]
        {:attribute-type t
         :search-type :value
         :name n
         :value v})
      4
      (let [[t n minv maxv] parts]
        {:attribute-type t
         :search-type :range
         :name n
         :min-value minv
         :max-value maxv})

      ;; else
      {:errors [(invalid-num-parts-msg)]})))

(def attribute-type->parser-fn
  "A map of attribute types to functions that can parse a value"
  {:string identity
   ;; TODO add more parsers later
   :float #(Double/parseDouble %)})

(defmulti parse-component-values
  "Parses the component type into their expected values"
  (fn [components]
    (:search-type components)))

(defmethod parse-component-values :range
  [components]
  (let [{:keys [attribute-type min-value max-value]} components]
    (if (or min-value max-value)
      (let [parser (attribute-type->parser-fn attribute-type)]
        (-> components
            (update-in [:min-value] #(when % (parser %)))
            (update-in [:max-value] #(when % (parser %)))))
      {:errors [(one-of-min-max-msg)]})))

(defmethod parse-component-values :value
  [components]
  (let [{:keys [attribute-type value]} components]
    (if (:value components)
      (let [parser (attribute-type->parser-fn attribute-type)]
        (try
          (update-in components [:value] parser)
          (catch NumberFormatException e
            {:errors [(invalid-value-msg attribute-type value)]}))
        )
      {:errors [(invalid-value-msg attribute-type value)]})))

(defn parse-component-type
  "Parses the type and it's values"
  [components]
  (if-let [type (some (set qm/attribute-types) [(keyword (:attribute-type components))])]
    (parse-component-values (assoc components :attribute-type type))
    {:errors [(invalid-type-msg (:attribute-type components))]}))

(defn parse-value
  "Parses an additional attribute value into it's constituent parts"
  [value]
  (let [components (value->components value)]
    (if (:errors components)
      components
      (parse-component-type components))))

;; TODO see if we can do this earlier
(defmulti attribute-components->condition
  "TODO"
  (fn [components]
    (:search-type components)))

(defmethod attribute-components->condition :value
  [components]
  (let [{:keys [attribute-type name value]} components]
    (qm/->AttributeValueCondition attribute-type name value)))

(defmethod attribute-components->condition :range
  [components]
  (let [{:keys [attribute-type name min-value max-value]} components]
    (qm/->AttributeRangeCondition attribute-type name min-value max-value)))


;; Converts parameter and values into collection query condition
(defmethod p/parameter->condition :attribute
  [concept-type param value options]

  (->> value
       (map parse-value)
       (map attribute-components->condition))

  ;;TODO only supporting granule for now

  #_(qm/->CollectionQueryCondition (p/parameter->condition :collection param value options))
)