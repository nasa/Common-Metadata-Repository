(ns cmr.umm.validation.utils
  "This contains utility methods for helping perform validations."
  (:require [bouncer.core :as b]
            [bouncer.validators :as v :refer [defvalidator]]
            [cmr.common.services.errors :as e]
            [clojure.string :as str])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))

;; Utility functions

(def umm-metadata-path-map
  "A map of metadata-format concept-type tuples to a map that maps UMM fields to the equivalent
  field names for that metadata-format. The values in that map are either a string field name
  or a tuple of a field name and another map of the fields within that part of the tree."
  ;; We need to make this work for something like DIF temporal or spatial where the mappings
  ;; won't be as straightforward.

  {[:echo10 :collection]
   {:access-value "RestrictionFlag"
    :product-specific-attributes "AdditionalAttributes"
    :spatial-coverage ["Spatial" {:granule-spatial-representation "GranuleSpatialRepresentation"}]}

   [:dif :collection]
   {;; This XPath will select the granule spatial representation.
    ;; /DIF/./Extended_Metadata/Metadata[Name="GranuleSpatialRepresentation"]/Value
    :product-specific-attributes "AdditionalAttributes"
    :spatial-coverage ["." {:granule-spatial-representation
                            {:xpath "Extended_Metadata/Metadata[Name=\"GranuleSpatialRepresentation\"]/Value"
                             :human "GranuleSpatialRepresentation"}}]}
   })

(defn- umm-path->format-type-path
  "Converts a path of UMM field keywords into a path specific for the metadata format and concept type."
  [metadata-format concept-type umm-path]
  (loop [format-type-map (umm-metadata-path-map [metadata-format concept-type])
         field-path umm-path
         new-path []]
    (if (seq field-path)
      (let [format-type-map-value (get format-type-map (first field-path))
            ;; The value in the map could be a vector containing the name of the equivalent element and a submap
            ;; or in the case of a leaf node it will just be the name of the element.
            [format-name-or-map submap] (if (sequential? format-type-map-value)
                                          format-type-map-value
                                          [format-type-map-value])
            format-name (or (:human format-name-or-map) format-name-or-map)]
        (when-not format-type-map-value
          (e/internal-error!
            (format
              "Could not find umm-metadata-path-map entry for %s of metadata-format %s and concept-type %s"
              (pr-str umm-path) metadata-format concept-type)))

        (recur submap (rest field-path) (conj new-path format-name)))
      new-path)))

(defn- message-fn
  "The message function used with bouncer validation. Avoids constructing the individual messages
  during validation so they can be customized per format later after validation is complete."
  [m]
  {:default-message-format (get-in m [:metadata :default-message-format])
   :format-fn (get-in m [:metadata :format-fn] format)
   :value (:value m)})

(defn- flatten-field-errors
  "Takes a nested set of errors as would be returned by bouncer and returns a flattened set of tuples
  containing the umm field path and the errors."
  ([field-errors]
   (flatten-field-errors field-errors []))
  ([field-errors field-path]
   (mapcat (fn [[field v]]
             (if (sequential? v)
               [[(conj field-path field) v]]
               (flatten-field-errors v (conj field-path field))))
           field-errors)))

(defn- create-format-specific-error-messages
  "Takes a list of field error tuples and errors (as returned by message-fn) and formats each error
  using the name appropriate for the metadata format. For example RestrictionFlag would be returned
  in an error message instead of the umm term Access value for ECHO10 format data."
  [metadata-format concept-type field-errors]
  (for [[field-path errors] field-errors
        :let [format-type-path (umm-path->format-type-path metadata-format concept-type field-path)]
        {:keys [default-message-format value format-fn]} errors]
    (format-fn default-message-format (last format-type-path) value)))

(def umm-type->concept-type
  {UmmCollection :collection
   UmmGranule :granule})

(defn perform-validation
  "Validates the umm record returning a list of error messages appropriate for the given metadata
  format and concept type. Returns an empty sequence if it is valid."
  [metadata-format umm validations]
  (->> (b/validate message-fn umm validations)
       first
       flatten-field-errors
       (create-format-specific-error-messages metadata-format (umm-type->concept-type (type umm)))))



;; Common validations

(defvalidator unique-by-name-validator
  {:default-message-format "%s must be unique. It contained duplicate names [%s]."
   :optional true
   :format-fn (fn [message-format field values]
                (let [freqs (frequencies (map :name values))
                      duplicate-names (for [[v freq] freqs :when (> freq 1)] v)]
                  (format message-format field (str/join ", " duplicate-names))))}
  [values]
  (= (count values) (count (distinct (map :name values)))))
