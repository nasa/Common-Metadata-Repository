(ns cmr.sizing.formats
  (:require
    [clojure.string :as string]
    [cmr.exchange.common.util :as util]
    [taoensso.timbre :as log]))

;;; Note that all internal operations for estimating size are performed
;;; assuming bytes as the units.

(defn- read-number
  "If value is string, read-string.  Otherwise ignore."
  [value]
  (if (string? value)
    (read-string value)
    value))

(defn- get-dimensionality
  "Get the dimensionality, which is the multiplication of all the sizes
  in all the Dimensions."
  [variable]
  (let [dims (get-in variable [:umm :Dimensions])]
    (reduce * (map :Size dims))))

(defn- get-measurement
  [variable]
  (let [total-dimensionality (get-dimensionality variable)
        data-type (get-in variable [:umm :DataType])]
    (* total-dimensionality
       (util/data-type->bytes data-type))))

(defn- get-avg-gran-size
  "Gets SizeEstimation value for AverageSizeOfGranulesSampled and parses it to a number."
  [variable]
  (-> variable
      (get-in [:umm :SizeEstimation :AverageSizeOfGranulesSampled])
      read-number))

(defn- get-avg-compression-rate-netcdf4
  "Gets SizeEstimation value for AvgCompressionRateNetCDF4 and parses it to a number."
  [variable]
  (-> variable
      (get-in [:umm :SizeEstimation :AvgCompressionRateNetCDF4])
      read-number))

(defn- get-avg-fill-value-digit-number
  "Get the average digit number for each value of FillValues in the variable.
  For example, FillValues containing [{:Value 1} {:Value 100}], returns (1+3)/2=2."
  [variable]
  (let [fvs (get-in variable [:umm :FillValues])
        fvs-digit-numbers (map #(count (str (:Value %))) fvs)]
    (/ (reduce + fvs-digit-numbers) (count fvs-digit-numbers))))

(defn- get-avg-valid-range-digit-number
  "Get the average digit number for each range of ValidRanges in the variable.
  For example, ValidRanges containing [{:Min 0 :Max 100} {:Min 10 :Max 1000}] returns
  (1+3+2+4)/4=2.5"
  [variable]
  (let [vrs (get-in variable [:umm :ValidRanges])
        vrs-min-max (concat (map :Min vrs) (map :Max vrs))
        vrs-digit-numbers (map #(count (str %)) vrs-min-max)]
    (/ (reduce + vrs-digit-numbers) (count vrs-digit-numbers))))

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
        measurements (reduce + (map get-measurement variables))]
    (log/info (format "request-id: %s compression: %s measurements: %s"
                      (:request-id params) compression measurements))
    (+ (* granule-count compression measurements)
       (* granule-count metadata))))

(defn- estimate-ascii-size
  "Calculates the estimated size for ASCII.
  In ASCII output, the actual size for a granule in bytes is the sum of digit number
  of all the dimension data + 2 bytes(comma and space) for each data except for the last one.
  To estimate, we assume 50% dimension data has the average FillValues digit number.
  the other 50% dimention data has the average ValidRanges digit number.
  The estimated size = 0.5 * avg-fill-value-digit-number * dimensionality +
                       0.5 * avg-valid-range-digit-number * dimensionality +
                       2 * (dimensionality -1)"
  [granule-count variables params]
  (* granule-count  
     (reduce (fn [total-estimate variable]
               (let [dimensionality (get-dimensionality variable) 
                     avg-fill-value-digit-number (get-avg-fill-value-digit-number variable)
                     avg-valid-range-digit-number (get-avg-valid-range-digit-number variable)]
                 (log/info (format (str "request-id: %s variable-id: %s total-estimate: %s "
                                        "dimensionality: %s avg-fill-value-digit-number: %s "
                                        "avg-valid-range-digit-number: %s")
                                   (:request-id params) (get-in variable [:meta :concept-id])
                                   total-estimate dimensionality avg-fill-value-digit-number 
                                   avg-valid-range-digit-number))
                 (+ total-estimate
                    (* 0.5 avg-fill-value-digit-number dimensionality)
                    (* 0.5 avg-valid-range-digit-number dimensionality)
                    (* 2 (- dimensionality 1))))) 
             0
             variables)))

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
