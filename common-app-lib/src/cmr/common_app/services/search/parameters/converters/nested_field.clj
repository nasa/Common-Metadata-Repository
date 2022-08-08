(ns cmr.common-app.services.search.parameters.converters.nested-field
  "Contains functions for converting query parameters to conditions for nested fields."
  (:require
   [clojure.string :as string]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as p]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.transmit.kms :as kms]))

(def variable-subfields
  "The subfields of variable nested field."
  [:measurement :variable])

(def temporal-facet-subfields
  "The subfields of the granule temporal facet nested field."
  [:year :month :day])

(def cycle-passes-subfields
  "The subfields of the granule cycle-pass nested field."
  [:pass])

(def pass-subfields
  "The subfields of the granule pass nested field."
  [:pass :tiles])

(def measurement-identifier-subfields
  "The subfields of the variable measurement identifier nested field."
  [:contextmedium :object :quantity])

(defn get-subfield-names
  "Returns all of the subfields for the provided nested field. All nested field
  queries also support 'any'."
  [parent-field]
  ;; Remove any modifiers from parent field, e.g. :science-keyword-humanized -> :science-keyword
  ;; and :science-keywords-h to :science-keywords
  (let [base-parent-field (-> parent-field
                              name
                              (string/replace #"\..*$" "")
                              (string/replace #"-h$" "")
                              (string/replace #"-humanized$" "")
                              keyword)]
    (condp = base-parent-field
      :variables variable-subfields
      :temporal-facet temporal-facet-subfields
      :passes pass-subfields
      :measurement-identifiers measurement-identifier-subfields
      :platforms2 (conj (:platforms kms/keyword-scheme->field-names) :any)
      ;; else
      (conj (kms/keyword-scheme->field-names
             (kms/translate-keyword-scheme-to-gcmd base-parent-field))
            :any))))

(defn get-printable-subfields
  "Returns a string representation of the subfields for the given parent field."
  [parent-field]
  (let [sub-fields (get-subfield-names parent-field)]
    (pr-str (mapv name sub-fields))))

(defn- nested-field->elastic-keyword
  "Returns the elastic keyword for the given nested field and subfield.

  Example:
  (nested-field->elastic-keyword :science-keywords :category) returns :science-keywords.category."
  [parent-field subfield]
  (keyword (str (name parent-field) "." (name subfield))))

(defn- nested-field+value->string-condition
  "Converts a science keyword field and value into a string condition"
  [parent-field subfield value case-sensitive? pattern?]
  (if (sequential? value)
    (qm/string-conditions (nested-field->elastic-keyword parent-field subfield) value
                          case-sensitive? pattern? :or)
    (qm/string-condition (nested-field->elastic-keyword parent-field subfield) value
                         case-sensitive? pattern?)))

(def subfield-name->condition-type
  "Defines the subfield-name to non-string condition type mapping"
  {:pass :int
   ;; tiles could be comma separated string or list
   :tiles :list})

(defmulti nested-field+value->condition
  "Converts a nested field and its value into a condition"
  (fn [parent-field subfield-name subfield-value case-sensitive? pattern?]
    (subfield-name->condition-type subfield-name)))

(defmethod nested-field+value->condition :int
  [parent-field subfield-name subfield-value _ _]
  (qm/numeric-value-condition
   (nested-field->elastic-keyword parent-field subfield-name)
   subfield-value))

(defmethod nested-field+value->condition :list
  [parent-field subfield-name subfield-value case-sensitive? pattern?]
  (let [normalized-values (p/normalized-list-value subfield-value)]
    (nested-field+value->string-condition
     parent-field subfield-name normalized-values case-sensitive? pattern?)))

(defmethod nested-field+value->condition :default
  [parent-field subfield-name subfield-value case-sensitive? pattern?]
  (nested-field+value->string-condition
   parent-field subfield-name subfield-value case-sensitive? pattern?))

(defn parse-nested-condition
  "Converts a nested condition into a nested query model condition."
  [parent-field query-map case-sensitive? pattern?]
  (qm/nested-condition
   parent-field
   (gc/and-conds
    (map (fn [[subfield-name subfield-value]]
           (if (= :any subfield-name)
             (gc/or-conds
              (map #(nested-field+value->condition parent-field % subfield-value
                                                   case-sensitive? pattern?)
                   (get-subfield-names parent-field)))
             (nested-field+value->condition parent-field subfield-name subfield-value
                                            case-sensitive? pattern?)))
         (dissoc query-map :pattern :ignore-case)))))
