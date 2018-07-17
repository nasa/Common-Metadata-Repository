(ns cmr.umm-spec.migration.version.variable
  "Contains functions for migrating between versions of the UMM Variable schema."
  (:require
   [cmr.umm-spec.migration.version.interface :as interface]))

(defn sample-11->12
  [x]
  {:MeasurementConditions (:MeasurementConditions x)
   :ReportingConditions (:ReportingConditions x)})

(defn measurement-11->12
  [x]
  {:MeasurementSource (:MeasurementSource x)
   :MeasurementName {:MeasurementObject (:MeasurementName x)}})

(defn measurement-12->11
  [x]
  {:MeasurementSource (:MeasurementSource x)
   :MeasurementName (get-in x [:MeasurementName :MeasurementObject])})

(defn dimensions-12->11
  [dims]
  (mapv #(dissoc % :Type) dims))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Variable Migration Implementations

(defmethod interface/migrate-umm-version [:variable "1.0" "1.1"]
  ;; Migrate up to 1.1
  [context v & _]
  v)

(defmethod interface/migrate-umm-version [:variable "1.1" "1.0"]
  ;; Migrate down to 1.0
  [context v & _]
  (dissoc v :Services))

(defmethod interface/migrate-umm-version [:variable "1.1" "1.2"]
  ;; Migrate up to 1.2
  [context v & _]
  (-> v
      (assoc :Characteristics (mapv #(select-keys % [:GroupPath])
                                    (:Characteristics v))
             :MeasurementIdentifiers (mapv measurement-11->12 (:Measurements v))
             :SamplingIdentifiers (mapv sample-11->12 (:Characteristics v)))
      (dissoc :Measurements)))

(defmethod interface/migrate-umm-version [:variable "1.2" "1.1"]
  ;; Migrate down to 1.1
  [context v & _]
  (-> v
      (assoc :Characteristics (mapv #(select-keys % [:GroupPath])
                                    (:Characteristics v))
             ;; Note that there is no characteristics/*conditions x-form
             ;; because there's no way to definitely correlate indices of separate
             ;; arrays when trying to construct a 1.1 Characteristics array by
             ;; combining 1.2 Characteristics and 1.2 SamplingIdentifier arrays.
             :Measurements (mapv measurement-12->11 (:MeasurementIdentifiers v)))
      (update :Dimensions dimensions-12->11)
      (dissoc :MeasurementIdentifiers
              :SamplingIdentifiers
              :VariableSubType)))
