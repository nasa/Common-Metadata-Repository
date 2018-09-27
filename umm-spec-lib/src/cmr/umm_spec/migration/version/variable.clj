(ns cmr.umm-spec.migration.version.variable
  "Contains functions for migrating between versions of the UMM Variable schema."
  (:require
   [cmr.common.util :as util]
   [cmr.umm-spec.migration.version.interface :as interface]))

(defn sample-1-1->1-2
  [x]
  {:MeasurementConditions (:MeasurementConditions x)
   :ReportingConditions (:ReportingConditions x)})

(defn measurement-1-1->1-2
  [x]
  {:MeasurementSource (:MeasurementSource x)
   :MeasurementName {:MeasurementObject (:MeasurementName x)}})

(defn measurement-1-2->1-1
  [x]
  {:MeasurementSource (:MeasurementSource x)
   :MeasurementName (get-in x [:MeasurementName :MeasurementObject])})

(defn dimensions-1-2->1-1
  [dims]
  (mapv #(dissoc % :Type) dims))

(defn index-ranges-1-2->1-3
  "Removes any invalid index ranges. Returns nil if index-ranges is empty."
  [index-ranges]
  (if (or (> 2 (count (:LatRange index-ranges)))
          (> 2 (count (:LonRange index-ranges))))
    nil
    index-ranges))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Variable Migration Implementations

(defmethod interface/migrate-umm-version [:variable "1.0" "1.1"]
  ;; Migrate up to 1.1
  [context v & _]
  (dissoc v :Services))

(defmethod interface/migrate-umm-version [:variable "1.1" "1.0"]
  ;; Migrate down to 1.0
  [context v & _]
  v)

(defmethod interface/migrate-umm-version [:variable "1.1" "1.2"]
  ;; Migrate up to 1.2
  [context v & _]
  (-> v
      (assoc :MeasurementIdentifiers (mapv measurement-1-1->1-2 (:Measurements v))
             :SamplingIdentifiers (mapv sample-1-1->1-2 (:Characteristics v)))
      (dissoc :Measurements :Characteristics)))

(defmethod interface/migrate-umm-version [:variable "1.2" "1.1"]
  ;; Migrate down to 1.1
  [context v & _]
  (-> v
      (assoc :Measurements (mapv measurement-1-2->1-1 (:MeasurementIdentifiers v)))
      (update :Dimensions dimensions-1-2->1-1)
      (dissoc :MeasurementIdentifiers
              ;; Note that there is no characteristics/*conditions x-form
              ;; because there's no way to definitely correlate indices of separate
              ;; arrays when trying to construct a 1.1 Characteristics array by
              ;; combining 1.2 Characteristics and 1.2 SamplingIdentifier arrays.
              :Characteristics
              :SamplingIdentifiers
              :VariableSubType)))

(defmethod interface/migrate-umm-version [:variable "1.3" "1.2"]
  [context v & _]
  ;; Migrate down to 1.2
  (-> v
      (util/update-in-each [:Characteristics] update :IndexRanges index-ranges-1-2->1-3)
      (util/update-in-each [:Characteristics] util/remove-nil-keys)
      (dissoc :SizeEstimation
              :Alias)))

(defmethod interface/migrate-umm-version [:variable "1.2" " 1.3"]
  ;; Migrate up to 1.3
  [context v & _]
  v)
