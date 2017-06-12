(ns cmr.umm-spec.validation.platform
  "Defines validations for UMM collection platform."
  (:require [cmr.common.validations.core :as v]
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
   short name."
  [values unique-fields]
  (let [{:keys [ShortName Characteristics Name Value]} unique-fields]
    (map (fn [value]
           (apply str
                 (ShortName value)
                 (map #(str (Name %) (Value %)) (Characteristics value))))
         values)))

(defn- unique-platform-validator
  "Validates a list of items is unique by a specified field. Takes the name field and returns a
  new validator."
  [unique-fields]
  (fn valid [field-path values]
    (let [freqs (frequencies (get-platform-identifiers values unique-fields))]
      (when-let [duplicate-names (seq (for [[v freq] freqs :when (> freq 1)] v))]
        {field-path [(format "%%s must be unique. This contains duplicates named [%s]."
                             (clojure.string/join ", " duplicate-names))]}))))

(def platforms-validation
  "Defines the list of validation functions for validating collection platforms"
  [(v/every platform-validations)
   (unique-platform-validator unique-fields)])
