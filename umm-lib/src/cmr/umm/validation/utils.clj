(ns cmr.umm.validation.utils
  "This contains utility methods for helping perform validations."
  (:require [cmr.common.validations.core :as v]
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
    :spatial-coverage ["Spatial" {:granule-spatial-representation "GranuleSpatialRepresentation"
                                  :geometries "Geometries"}]
    :projects "Campaigns"
    :platforms ["Platforms"
                {:instruments ["Instruments"
                               {:sensors ["Sensors" {:characteristics "Sensor characteristics"}]
                                :characteristics "Instrument characteristics"}]
                 :characteristics "Platform characteristics"}]
    :associated-difs "AssociatedDIFs"
    :temporal ["Temporal" {:range-date-times "RangeDateTimes"}]}

   [:dif :collection]
   {;; This XPath will select the granule spatial representation.
    ;; /DIF/./Extended_Metadata/Metadata[Name="GranuleSpatialRepresentation"]/Value
    :product-specific-attributes "AdditionalAttributes"

    ;; TODO this is a temporary example of how we might be able to have paths that can generate an
    ;; XPath for errors as well as a human readable name for a field.
    :spatial-coverage ["." {:granule-spatial-representation
                            {:xpath "Extended_Metadata/Metadata[Name=\"GranuleSpatialRepresentation\"]/Value"
                             :human "GranuleSpatialRepresentation"}
                            :geometries "Geometry"}]
    :projects "Project"}

   [:iso-smap :collection]
   {:spatial-coverage ["." {:geometries "Geometry"}]}

   [:iso19115 :collection]

   ;; TODO Update to use the :xpath notation once it is finished, for now just hardcode a string
   ;; I emailed Katie to ask her what name should go here.
   {:projects "MI_Metadata/acquisitionInformation/MI_AcquisitionInformation/operation/MI_Operation"
    :spatial-coverage ["." {:geometries "Geometry"}]}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility functions

(defmulti umm-field->format-type-field+submap
  "Converts a umm field to the given format type field. Takes a umm field like :project and a
  mapping of umm fields (at the level of the umm field) and returns the equivalent metadata format
  specific field (like \"Campaign\""
  (fn [umm-field format-type-map]
    (type umm-field)))

(defmethod umm-field->format-type-field+submap Long
  [idx format-type-map]
  ;; A long indicates an index into a list. There is no translation that is done here.
  [idx format-type-map])

(defmethod umm-field->format-type-field+submap clojure.lang.Keyword
  [umm-field format-type-map]
  (let [format-type-map-value (get format-type-map umm-field)
        ;; The value in the map could be a vector containing the name of the equivalent element and a submap
        ;; or in the case of a leaf node it will just be the name of the element.
        [format-field-or-map submap] (if (sequential? format-type-map-value)
                                       format-type-map-value
                                       [format-type-map-value])]
    [(or (:human format-field-or-map) format-field-or-map) submap]))


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
      (let [[format-field submap] (umm-field->format-type-field+submap
                                    (first field-path) format-type-map)]
        (when-not format-field
          (e/internal-error!
            (format
              "Could not find umm-metadata-path-map entry for %s of metadata-format %s and concept-type %s"
              (pr-str umm-path) metadata-format concept-type)))

        (recur submap (rest field-path) (conj new-path format-field)))
      new-path)))

(defn- create-format-specific-error-messages
  "Takes a list of field error tuples and errors and formats each error using the name appropriate
  for the metadata format. For example RestrictionFlag would be returned in an error message instead
  of the umm term Access value for ECHO10 format data."
  [metadata-format concept-type umm field-errors]
  (for [[field-path errors] field-errors
        :let [format-type-path (umm-path->format-type-path metadata-format concept-type field-path)]
        error-format errors]
    (format error-format (last format-type-path))))

(def umm-type->concept-type
  {UmmCollection :collection
   UmmGranule :granule})

(defn perform-validation
  "Validates the umm record returning a list of error messages appropriate for the given metadata
  format and concept type. Returns an empty sequence if it is valid."
  [metadata-format umm validation]
  (->> (v/validate validation umm)
       (create-format-specific-error-messages
         metadata-format
         (umm-type->concept-type (type umm))
         umm)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common validations


(defn unique-by-name-validator
  "Validates a list of items is unique by a specified field. Takes the name field and returns a
  new validator."
  [name-field]
  (fn [field-path values]
    (let [freqs (frequencies (map name-field values))]
      (when-let [duplicate-names (seq (for [[v freq] freqs :when (> freq 1)] v))]
        {field-path [(format "%%s must be unique. This contains duplicates named [%s]."
                             (str/join ", " duplicate-names))]}))))






