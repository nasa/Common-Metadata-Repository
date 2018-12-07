(ns cmr.umm-spec.migration.version.variable
  "Contains functions for migrating between versions of the UMM Variable schema."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.umm-spec.migration.version.interface :as interface]))

(defn- sample-1-1->1-2
  [x]
  (let [{:keys [MeasurementConditions ReportingConditions]} x]
    (when (and MeasurementConditions ReportingConditions)
      [{:MeasurementConditions MeasurementConditions
       :ReportingConditions ReportingConditions}])))

(def ^:private measurement-sources
 #{"CSDMS", "CF", "BODC", "OTHER"})

(def ^:private fill-value-types
  #{"SCIENCE_FILLVALUE", "QUALITY_FILLVALUE", "ANCILLARY_FILLVALUE", "OTHER"})

(defn- enum-value
  "Returns the proper enum value for the allowed enum hash, the initial value and default"
  [enum-hash value default-value]
  (if value
    (if-let [new-value (enum-hash (string/upper-case value))]
      new-value
      default-value)
    default-value))

(defn- measurement-1-1->1-2
  [x]
  (let [{:keys [MeasurementSource MeasurementName]} x
        source (enum-value measurement-sources MeasurementSource "OTHER")]
    (when (and source MeasurementName)
      {:MeasurementSource source
       :MeasurementName {:MeasurementObject MeasurementName}})))

(defn- measurement-1-2->1-1
  [x]
  (when x
    {:MeasurementSource (:MeasurementSource x)
     :MeasurementName (get-in x [:MeasurementName :MeasurementObject])}))

(defn- fill-value-1-1->1-2
  [value]
  (if-let [fill-value-type (:Type value)]
    (assoc value :Type (enum-value fill-value-types fill-value-type "OTHER"))
    value))

(defn- fill-values-1-1->1-2
  [values]
  (when (seq values)
    (map fill-value-1-1->1-2 values)))

(defn- dimensions-1-2->1-1
  [dims]
  (map #(dissoc % :Type) dims))

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
      (assoc :MeasurementIdentifiers (->> (:Measurements v)
                                          (map measurement-1-1->1-2)
                                          (remove nil?)
                                          seq)
             :SamplingIdentifiers (sample-1-1->1-2 (:Characteristics v)))
      (dissoc :Measurements :Characteristics)
      (util/update-in-each [:Dimensions] assoc :Type "OTHER")
      (update :FillValues fill-values-1-1->1-2)
      util/remove-nil-keys))

(defmethod interface/migrate-umm-version [:variable "1.2" "1.1"]
  ;; Migrate down to 1.1
  [context v & _]
  (-> v
      (assoc :Measurements (map measurement-1-2->1-1 (:MeasurementIdentifiers v)))
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
      (dissoc :SizeEstimation
              :Alias)))

(defmethod interface/migrate-umm-version [:variable "1.2" " 1.3"]
  ;; Migrate up to 1.3
  [context v & _]
  v)
