(ns cmr.sizing.formats
  (:require
    [clojure.string :as string]
    [cmr.exchange.common.util :as util]
    [taoensso.timbre :as log]))

;;; Note that all internal operations for estimating size are performed
;;; assuming bytes as the units.

(def NetCDF-4
  "NetCDF-4")

(defn- read-number
  "If value is string, read-string.  Otherwise ignore."
  [value]
  (if (string? value)
    (read-string value)
    value))

(defn- get-dimensionality
  "Get the dimensionality, which is the multiplication of all the sizes
  in all the Dimensions. Returns 0 when Dimensions is not present."
  [variable]
  (let [dims (get-in variable [:umm :Dimensions])]
    (if (seq dims)
      (reduce * (map :Size dims))
      0)))

(defn- get-measurement
  [variable]
  (let [total-dimensionality (get-dimensionality variable)
        data-type (get-in variable [:umm :DataType])]
    (* total-dimensionality
       (util/data-type->bytes data-type))))

(defn- get-netcdf3-measurement
  [variable]
  (let [total-dimensionality (get-dimensionality variable)
        data-type (get-in variable [:umm :DataType])]
    (* total-dimensionality
       (max 2 (util/data-type->bytes data-type)))))

(defn- get-avg-gran-size
  "Gets SizeEstimation value for AverageSizeOfGranulesSampled and parses it to a number."
  [variable]
  (-> variable
      (get-in [:umm :SizeEstimation :AverageSizeOfGranulesSampled])
      read-number))

(defn- get-netcdf4-rate
  "Gets the :Rate value for :NetCDF-4 format in avg-comp-info."
  [avg-comp-info]
  (some #(when (= NetCDF-4 (:Format %)) (:Rate %)) avg-comp-info))  

(defn- get-avg-compression-rate-netcdf4
  "Gets :Rate value for NetCDF-4 format in :SizeEstimation of variable 
  and parses it to a number."
  [variable]
  (-> variable
      (get-in [:umm :SizeEstimation :AverageCompressionInformation])
      get-netcdf4-rate
      read-number))

(defn- get-avg-digit-number
  "Returns the average of all the numbers in digit-numbers.
  Return 0 when digit-numbers is empty."
  [digit-numbers]
  (if (seq digit-numbers)
    (/ (reduce + digit-numbers) (count digit-numbers))
    0))

(defn- get-avg-fill-value-digit-number
  "Get the average digit number for each value of FillValues in the variable.
  For example, FillValues containing [{:Value 1} {:Value 100}], returns (1+3)/2=2."
  [variable]
  (let [fvs (get-in variable [:umm :FillValues])
        fvs-digit-numbers (map #(count (str (:Value %))) fvs)]
    (get-avg-digit-number fvs-digit-numbers)))

(defn- process-range
  "Split the range into two parts if the range is from negative to positive.
  For example {:Min -10 :Max 100} becomes [{:Min -10 :Max 0} {:Min 0 :Max 100}]."
  [range]
  (let [min (:Min range)
        max (:Max range)]
    (if (and (< min 0) (> max 0))
      [{:Min min :Max 0} {:Min 0 :Max max}]
      range)))

(defn- get-valid-ranges 
  "Get the ValidRanges from variable. If the range is from negative
  to positive, break it up into two ranges: negative to 0 and 0 to positive."
  [variable]
  (let [vrs (get-in variable [:umm :ValidRanges])]
    (flatten (map process-range vrs))))

(defn- get-avg-valid-range-digit-number
  "Get the average digit number for each range of ValidRanges in the variable.
  For example, ValidRanges containing [{:Min -10 :Max 100} {:Min 10 :Max 1000}] 
  returns (3+1+1+3+2+4)/6=2.3"
  [variable]
  (let [vrs (get-valid-ranges variable)
        vrs-min-max (concat (map :Min vrs) (map :Max vrs))
        vrs-digit-numbers (map #(count (str %)) vrs-min-max)]
    (get-avg-digit-number vrs-digit-numbers)))

(defn estimate-dods-size
  "Calculates the estimated size for DODS format."
  [granule-count variables params]
  (let [compression 1
        metadata 0 ; included here for symmetry
        measurements (reduce + (map get-measurement variables))]
    (log/info (format "request-id: %s metadata: %s compression: %s measurements: %s"
                      (:request-id params) metadata compression measurements))
    (+ (* granule-count compression measurements) metadata)))

(defn- estimate-netcdf3-size
  "Calculates the estimated size for NETCDF3 format."
  [granule-count variables metadata params]
  (let [compression 1
        measurements (reduce + (map get-netcdf3-measurement variables))]
    (log/info (format "request-id: %s compression: %s measurements: %s"
                      (:request-id params) compression measurements))
    (+ (* granule-count compression measurements)
       (* granule-count metadata))))

(defn- estimate-ascii-size-per-granule
  "Calculate the estimated size for ASCII output per granule, for the variable.
  In ASCII output, the actual size for a granule in bytes is the sum of digit number
  of all the dimension data + 2 bytes(comma and space) for each data except for the last one.
  To estimate, we have the following cases: 
  1. dimensionality <= 0.
     Returns 0.
  2. avg-fill-value-digit-number = 0, avg-valid-range-digit-number != 0
     Returns avg-valid-range-digit-number * dimensionality + 2 * (dimensionality -1)
  3. avg-fill-value-digit-number != 0, avg-valid-range-digit-number = 0
     Returns avg-fill-value-digit-number * dimensionality + 2 * (dimensionality -1)
  4. avg-fill-value-digit-number = 0, avg-valid-range-digit-number = 0
     Returns 3 * dimensionality + 2 * (dimensionality -1)
  5. when none of these values are 0
     Returns  0.5 * avg-fill-value-digit-number * dimensionality +
              0.5 * avg-valid-range-digit-number * dimensionality +
              2 * (dimensionality -1)"
  [variable params total-estimate]
  (let [dimensionality (get-dimensionality variable)
        avg-fill-value-digit-number (get-avg-fill-value-digit-number variable)
        avg-valid-range-digit-number (get-avg-valid-range-digit-number variable)]
    (log/info (format (str "request-id: %s variable-id: %s total-estimate: %s "
                           "dimensionality: %s avg-fill-value-digit-number: %s "
                           "avg-valid-range-digit-number: %s")
                      (:request-id params) (get-in variable [:meta :concept-id])
                      total-estimate dimensionality avg-fill-value-digit-number
                      avg-valid-range-digit-number))
    (cond 
      (<= dimensionality 0) 0
      (and (= 0 avg-fill-value-digit-number) 
           (not= 0 avg-valid-range-digit-number)) (+ (* avg-valid-range-digit-number dimensionality)
                                                     (* 2 (dec dimensionality)))
      (and (not= 0 avg-fill-value-digit-number) 
           (= 0 avg-valid-range-digit-number)) (+ (* avg-fill-value-digit-number dimensionality)
                                                  (* 2 (dec dimensionality))) 
      (and (= 0 avg-fill-value-digit-number)
           (= 0 avg-valid-range-digit-number)) (+ (* 3 dimensionality)
                                                  (* 2 (dec dimensionality)))
      :else (+ (* 0.5 avg-fill-value-digit-number dimensionality)
               (* 0.5 avg-valid-range-digit-number dimensionality)
               (* 2 (dec dimensionality))))))
    
(defn- estimate-ascii-size
  "Calculates the estimated size for ASCII output for all the variables and the granule-count."
  [granule-count variables params]
  (* granule-count
     (long (reduce (fn [total-estimate variable]
                     (+ total-estimate
                        (estimate-ascii-size-per-granule variable params total-estimate)))
                   0
                   variables))))

(defn- estimate-netcdf4-size
  "Calculates the estimated size for NETCDF4 format.
   total-granule-input-bytes is a value given by the client in the size estimate request."
  [granule-count variables params]
  (reduce (fn [total-estimate variable]
            (let [avg-gran-size (get-avg-gran-size variable)
                  total-granule-input-bytes (read-string (:total-granule-input-bytes params))
                  avg-compression-rate (get-avg-compression-rate-netcdf4 variable)]
              (log/info (format (str "request-id: %s variable-id: %s total-estimate: %s "
                                     "avg-gran-size: %s total-granule-input-bytes: %s "
                                     "avg-compression-rate: %s")
                                (:request-id params) (get-in variable [:meta :concept-id])
                                total-estimate avg-gran-size total-granule-input-bytes
                                avg-compression-rate))
              (+ total-estimate
                 (* total-granule-input-bytes
                    avg-compression-rate
                    (/ (/ total-granule-input-bytes granule-count) avg-gran-size)))))
          0
          variables))

(defn estimate-size
  [fmt granule-count vars granule-metadata-size params]
  (case (keyword (string/lower-case fmt))
    :dods (estimate-dods-size granule-count vars params)
    :nc (estimate-netcdf3-size granule-count vars granule-metadata-size params)
    :nc4 (estimate-netcdf4-size granule-count vars params)
    :ascii (estimate-ascii-size granule-count vars params)
    (do
      (log/errorf "Cannot estimate size for %s (not implemented)." fmt)
      {:errors ["not-implemented"]})))
