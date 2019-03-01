(ns cmr.search.services.parameters.validation.util
  "Contains utility functions for validating parameters."
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.common-app.services.search.parameters.converters.nested-field :as nf]))

(defn nested-field-validation-for-subfield
  "Validates that the provided subfield is valid for the given nested field."
  [field concept-type params error-msg]
  (when-let [param-values (get params field)]
    (if (map? param-values)
      (let [values (vals param-values)]
        (if (some #(not (map? %)) values)
          [error-msg]
          (reduce
            (fn [errors param]
              (if-not (some #{param} (nf/get-subfield-names field))
                (conj errors (format (str "Parameter [%s] is not a valid [%s] search term. "
                                          "The valid search terms are %s.")
                                     (name param)
                                     (csk/->snake_case_string field)
                                     (nf/get-printable-subfields field)))
                errors))
            []
            (mapcat keys values))))
      [error-msg])))
