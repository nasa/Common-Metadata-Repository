(ns cmr.umm.validation.utils
  "This contains utility methods for helping perform validations.

  The bouncer library (https://github.com/leonardoborges/bouncer) is used for defining validations.
  We're using some of the features of bouncer to change how message formatting is done. We have a
  requirement to use field names corresponding to the field names of the ingested format like ECHO10
  and DIF. (See CMR-963) Bouncer by default will use the name of the field from the record being
  validated. This namespace includes code to delay the setting of that field information until after
  validation. The UMM field paths are translated into the equivalent paths in the source format.

  An additional extension to the bouncer library is the addition of a :format-fn in the metadata of
  a validator. The :format-fn defines a function that will take the message format string, field name,
  and the value that had an error to generate an error message. The purpose of this is to allow error
  messages to include details about the values that had an error. See unique-by-name-validator for
  an example of defining a custom format-fn."
  (:require [bouncer.core :as b]
            [bouncer.validators :as v :refer [defvalidator]]
            [cmr.common.services.errors :as e]
            [clojure.string :as str])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))

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

    ;; TODO this is a temporary example of how we might be able to have paths that can generate an
    ;; XPath for errors as well as a human readable name for a field.
    :spatial-coverage ["." {:granule-spatial-representation
                            {:xpath "Extended_Metadata/Metadata[Name=\"GranuleSpatialRepresentation\"]/Value"
                             :human "GranuleSpatialRepresentation"}}]}
   })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility functions

(defn- umm-path->format-type-path
  "Converts a path of UMM field keywords into a path specific for the metadata format and concept
  type.

  Example:

  (umm-path->format-type-path
  :echo10 :collection [:spatial-coverage :granule-spatial-representation])
  => [\"Spatial\" \"GranuleSpatialRepresentation\"]

  [:spatial-coverage :granule-spatial-representation] represents a path within the UMM collection
  record to a specific field that might have an error. This function converts it to
  [\"Spatial\" \"GranuleSpatialRepresentation\"] which is the path to the equivalent item within
  ECHO10 XML.

  TODO For things like DIF the actual XPath to the granule spatial representation is
  /DIF/Extended_Metadata/Metadata[Name=\"GranuleSpatialRepresentation\"]/Value
  One of the requested features is the ability for error messages to include an XPath to the
  problematic area of the metadata. I'm going to schedule a meeting to talk with Patrick about this.
  He has asked for XPath but I'm not sure XPaths like the above are really usable in a client which
  must annotate form fields.
  See CMR-1169"
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
  during validation so they can be customized per format later after validation is complete. Instead
  of taking the message details and returning a string error it returns a subset of the data that
  was passed in. After validation has completed these are used to construct format specific
  error messages."
  [m]
  {:default-message-format (get-in m [:metadata :default-message-format])
   ;; The :format-fn will default to the standard Clojure format function.
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common validations

(defvalidator unique-by-name-validator
  {:default-message-format "%s must be unique. This contains duplicates named [%s]."
   :optional true ; don't run this validation if the value is nil.
   ;; Define a custom :format-fn to include the duplicate values in the error message.
   :format-fn (fn [message-format field values]
                (let [freqs (frequencies (map :name values))
                      duplicate-names (for [[v freq] freqs :when (> freq 1)] v)]
                  (format message-format field (str/join ", " duplicate-names))))}
  [values]
  (= (count values) (count (distinct (map :name values)))))
