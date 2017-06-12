(ns cmr.common-app.services.search.parameters.converters.nested-field
  "Contains functions for converting query parameters to conditions for nested fields."
  (:require
   [clojure.string :as str]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as p]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.transmit.kms :as kms]))

(def variable-sub-fields
  "The subfields of variable nested field."
  [:measurement :variable])

(defn get-subfield-names
  [parent-field]
  "Returns all of the subfields for the provided nested field. All nested field queries also support
  'any'."
  ;; Remove any modifiers from parent field, e.g. :science-keyword.humanized -> :science-keyword
  (let [base-parent-field (keyword (str/replace (name parent-field) #"\..*$" ""))]
    (if (= :variables base-parent-field)
      variable-sub-fields
      (conj (kms/keyword-scheme->field-names
             (kms/translate-keyword-scheme-to-gcmd base-parent-field))
            :any))))

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

(defn parse-nested-condition
  "Converts a nested condition into a nested query model condition."
  [parent-field query-map case-sensitive? pattern?]
  (qm/nested-condition
    parent-field
    (gc/and-conds
      (map (fn [[subfield-name subfield-value]]
             (if (= :any subfield-name)
               (gc/or-conds
                 (map #(nested-field+value->string-condition parent-field % subfield-value
                                                             case-sensitive? pattern?)
                      (get-subfield-names parent-field)))
               (nested-field+value->string-condition parent-field subfield-name subfield-value
                                                     case-sensitive? pattern?)))
           (dissoc query-map :pattern :ignore-case)))))
