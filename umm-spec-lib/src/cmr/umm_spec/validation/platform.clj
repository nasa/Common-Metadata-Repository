(ns cmr.umm-spec.validation.platform
  "Defines validations for UMM collection platform."
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.validation.umm-spec-validation-utils :as vu]))

(def ^:private sensor-validations
  "Defines the sensor validations for collections"
  {:Characteristics (vu/unique-by-name-validator :Name)})

(def ^:private instrument-validations
  "Defines the instrument validations for collections"
  {:ComposedOf [(v/every sensor-validations)
                (vu/unique-by-name-validator :ShortName)]
   :Characteristics (vu/unique-by-name-validator :Name)})

(def ^:private platform-validations
  "Defines the platform validations for collections"
  {:Instruments [(v/every instrument-validations)
                 (vu/unique-by-name-validator :ShortName)]
   :Characteristics (vu/unique-by-name-validator :Name)})

(def ^:private unique-fields
  "Defines the fields to use that combined create unique platform names.
  GCMD's keyword management system (KMS) defines aircraft, boats, ballons, etc.
  with the model number. So the short name is not specific enough to identify
  specific platform instances. Therefore we are adding Characteristic names and values
  so that specific aircraft tail numbers, or boat IDS, etc. can be specified with the
  short name."
  #{:ShortName :Characteristics :Name :Value})

(defn- get-platform-identifiers
  "Concatenates ShortName with Characteristic Name and Value fields.
   GCMD's keyword management system (KMS) defines aircraft, boats, ballons, etc.
   with the model number. So the short name is not specific enough to identify
   specific platform instances. Therefore we are adding Characteristic names and values
   so that specific aircraft tail numbers, or boat IDS, etc. can be specified with the
   short name. The Unicode Character 'SYMBOL FOR GROUP SEPARATOR' \u241D character is added as a delimiter between the fields. This is used
   in the getErrorString parameter-parser to parse out the different fields, so that a
   meaningful error string can be suppplied back to the user."
  [values unique-fields]
  (let [{:keys [ShortName Characteristics Name Value]} unique-fields]
    (map (fn [value]
           (apply str
                 (ShortName value)
                 "\u241D"
                 (map #(str (Name %) "\u241D" (Value %)) (Characteristics value))))
         values)))

(defn- getErrorString
  "Creates the error string for platforms depending on which elements (ShortName, Characteristic/Name, Characteristic/Value) are present.
   The element order is ShortName, Name, Value. The values are separated by the Unicode Character 'SYMBOL FOR GROUP SEPARATOR' \u241D character."
  [duplicate-name]
  (when duplicate-name
    (let [platform-elements (string/split duplicate-name #"\u241D")
          platform-counts (count platform-elements)
          shortname (first platform-elements)
          characteristic-name (second platform-elements)
          characteristic-value (last platform-elements)]
      ;; The case of 0 or greater than 3 shouldn't exist.  If it is either value then the validation
      ;; software is not working.
      (if-not (<= 1 platform-counts 3)
        (errors/throw-service-error (Exception. "The platform validation checking for duplicates either has 0 or more than 3 elements, neither is valid. There is a software problem."))
        (case platform-counts
          1  (format "The Platform ShortName [%s] must be unique. This record contains duplicates." shortname)
          2  (format "The combination of Platform ShortName [%s] along with its Characteristic Name [%s] must be unique. This record contains duplicates." shortname characteristic-name)
          3  (format "The combination of Platform ShortName [%s] along with its Characteristic Name [%s] and Characteristic Value [%s] must be unique. This record contains duplicates." shortname characteristic-name characteristic-value))))))

(defn- unique-platform-validator
  "Validates a list of items is unique by a specified field. Takes the name field and returns a
  new validator."
  [unique-fields]
  (fn valid [field-path values]
    (let [freqs (frequencies (get-platform-identifiers values unique-fields))]
      (when-let [duplicate-names (seq (for [[v freq] freqs :when (> freq 1)] v))]
        (let [errorMsgs (map getErrorString duplicate-names)]
          {field-path (vec errorMsgs)})))))

(def platforms-validation
  "Defines the list of validation functions for validating collection platforms"
  [(v/every platform-validations)
   (unique-platform-validator unique-fields)])
