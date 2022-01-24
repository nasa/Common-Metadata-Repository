(ns cmr.umm-spec.migration.version.variable
  "Contains functions for migrating between versions of the UMM Variable schema."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.version.interface :as interface]
   [cmr.umm-spec.util :as spec-util]))

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

(defmethod interface/migrate-umm-version [:variable "1.4" "1.3"]
  [context v & _]
  ;; Migrate down to 1.3
  (if-let [avg-comp-info (get-in v [:SizeEstimation :AverageCompressionInformation])]
    (as-> v m
      (if-let [avg-comp-ascii (some #(when (= "ASCII" (:Format %)) (:Rate %)) avg-comp-info)]
        (assoc-in m [:SizeEstimation :AvgCompressionRateASCII] avg-comp-ascii)
        m)
      (if-let [avg-comp-netcdf4 (some #(when (= "NetCDF-4" (:Format %)) (:Rate %)) avg-comp-info)]
        (assoc-in m [:SizeEstimation :AvgCompressionRateNetCDF4] avg-comp-netcdf4)
        m)
      (update-in m [:SizeEstimation] dissoc :AverageCompressionInformation)
      (util/remove-empty-maps m))
    v))

(defmethod interface/migrate-umm-version [:variable "1.3" "1.4"]
  ;; Migrate up to 1.4
  [context v & _]
  (let [avg-comp-info-ascii (when-let [rate (get-in v [:SizeEstimation :AvgCompressionRateASCII])]
                              [{:Rate rate :Format "ASCII"}])
        avg-comp-info-netcdf4 (when-let [rate (get-in v [:SizeEstimation :AvgCompressionRateNetCDF4])]
                                [{:Rate rate :Format "NetCDF-4"}])]
    (if (or avg-comp-info-ascii avg-comp-info-netcdf4)
      (-> v
          (update-in [:SizeEstimation] dissoc :AvgCompressionRateASCII :AvgCompressionRateNetCDF4)
          (assoc-in [:SizeEstimation :AverageCompressionInformation]
                    (concat avg-comp-info-ascii avg-comp-info-netcdf4)))
      v)))

(defmethod interface/migrate-umm-version [:variable "1.5" "1.4"]
  [context v & _]
  (dissoc v :AcquisitionSourceName))

(defmethod interface/migrate-umm-version [:variable "1.4" "1.5"]
  [context v & _]
  (assoc v :AcquisitionSourceName "Not Provided"))

(def ^:private default-measurement-object
  "not_specified")

(defn- measurement_1_6->measurement_1_5
  "Returns the v1.5 of MeasurementIdentifier for the given v1.6 MeasurementIdentifier"
  [measurement]
  (let [{:keys [MeasurementObject MeasurementQuantities]} measurement
        object (if MeasurementObject
                 MeasurementObject
                 default-measurement-object)
        quantity (:Value (first MeasurementQuantities))]
    {:MeasurementName (util/remove-nil-keys {:MeasurementObject object
                                             :MeasurementQuantity quantity})
     :MeasurementSource "OTHER"}))

(defn- dimension_type_1_6->dimension_type_1_5
  "Returns the v1.5 of Dimension Type for the given v1.6 Dimension Type"
  [dimension-type]
  (if (contains? #{"ALONG_TRACK_DIMENSION", "CROSS_TRACK_DIMENSION"} dimension-type)
    "OTHER"
    dimension-type))

(defn- dimension_1_6->dimension_1_5
  "Returns the v1.5 of Dimension for the given v1.6 Dimension"
  [dimension]
  (update dimension :Type dimension_type_1_6->dimension_type_1_5))

(defn- measurement_1_5->measurement_1_6
  "Returns the v1.6 of MeasurementIdentifier for the given v1.5 MeasurementIdentifier"
  [measurement]
  (let [{:keys [MeasurementObject MeasurementQuantity]} (:MeasurementName measurement)
        quantities (when MeasurementQuantity
                     [{:Value MeasurementQuantity}])]
    (util/remove-nil-keys {:MeasurementContextMedium "not_specified"
                           :MeasurementObject (if MeasurementObject
                                                MeasurementObject
                                                "not_specified")
                           :MeasurementQuantities quantities})))

(defmethod interface/migrate-umm-version [:variable "1.6" "1.5"]
  [context v & _]
  (-> v
      (util/update-in-each [:MeasurementIdentifiers] measurement_1_6->measurement_1_5)
      (util/update-in-each [:Dimensions] dimension_1_6->dimension_1_5)))

(defmethod interface/migrate-umm-version [:variable "1.5" "1.6"]
  [context v & _]
  (util/update-in-each v [:MeasurementIdentifiers] measurement_1_5->measurement_1_6))

(defmethod interface/migrate-umm-version [:variable "1.6" "1.7"]
  [context v & _]
  (-> v
      (assoc :IndexRanges (get-in v [:Characteristics :IndexRanges]))
      (dissoc :Alias :AcquisitionSourceName :Characteristics :SizeEstimation)))

(defmethod interface/migrate-umm-version [:variable "1.7" "1.6"]
  [context v & _]
  (-> v
      (assoc :AcquisitionSourceName spec-util/not-provided)
      (assoc-in [:Characteristics :IndexRanges] (get v :IndexRanges))
      (assoc-in [:Characteristics :GroupPath] (get v :Name))
      (dissoc :IndexRanges :StandardName)))

;; migrations for 1.8 **********************************************************

(defmethod interface/migrate-umm-version [:variable "1.7" "1.8"]
  [context umm-v & _]
  ;; insert a metadata specification
  (-> umm-v
      (m-spec/update-version :variable "1.8")))

(defmethod interface/migrate-umm-version [:variable "1.8" "1.7"]
  ;; drop metadata specification and related urls
  [context umm-v & _]
  (-> umm-v
      (dissoc :MetadataSpecification :RelatedURLs)))

;; migrations for 1.8.1 **********************************************************

(defmethod interface/migrate-umm-version [:variable "1.8" "1.8.1"]
  [context umm-v & _]
  ;; update the MetadataSpecification
  (-> umm-v
      (m-spec/update-version :variable "1.8.1")))

(defmethod interface/migrate-umm-version [:variable "1.8.1" "1.8"]
  [context umm-v & _]
  ;; Update the MetadataSpecification and Convert VariableType and VariableSubType
  (-> umm-v
      (update :VariableType #(if (= "COORDINATE" %) "OTHER" %))
      (update :VariableSubType #(if (or (= "LONGITUDE" %) (= "LATITUDE" %) (= "TIME" %)) "OTHER" %))
      (m-spec/update-version :variable "1.8")))
