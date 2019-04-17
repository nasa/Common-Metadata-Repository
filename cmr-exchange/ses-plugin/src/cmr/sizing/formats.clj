(ns cmr.sizing.formats
  (:require
    [clojure.string :as string]
    [cmr.exchange.common.util :as util]
    [taoensso.timbre :as log]))

;;; Note that all internal operations for estimating size are performed
;;; assuming bytes as the units.

(def default-format
  "This will map to different default formats for egi and opendap requests."
  "default")

(def egi-service-type
  "esi")

(def opendap-service-type
  "opendap")

(def egi-formats->ses-formats-mapping
  "Mapping the EGI supported formats(in lower-case) from echo forms to the
  SES supported formats."
  {"shapefile"     "shapefile"
   "tabular_ascii" "tabular_ascii"
   "geotiff"       "geotiff"
   "nc"            "nc"
   "netcdf"        "nc"
   "nc4"           "nc4"
   "netcdf4-cf"    "nc4"
   "netcdf4"       "nc4"
   "netcdf-4"      "nc4"
   "native"        "native"
   "default"       "native"})

(def opendap-formats->ses-formats-mapping
  "Mapping the OPeNDAP supported formats to the SES supported formats."
  {"nc"      "nc"
   "nc4"     "nc4"
   "ascii"   "ascii"
   "dods"    "dods"
   "default" "nc"})

(def service-type->formats-mapping
  "Mapping the service-type to the map of service supported format and
  SES supported formats."
  {"esi" egi-formats->ses-formats-mapping
   "opendap" opendap-formats->ses-formats-mapping})

(def ses-formats->compression-format-mapping
  "Mapping the ses supported formats to the formats in
  AverageCompressionInformation in UMM-VAR." 
  {"tabular_ascii" "ASCII"
   "nc4"           "NetCDF-4"
   "shapefile"     "ESRI Shapefile"
   "geotiff"       "GeoTIFF"
   "native"        "Native"})

(defn- read-number
  "If value is string, read-string.  Otherwise ignore."
  [value]
  (if (string? value)
    (read-string value)
    value))

(defn- get-alias
  "Get the alias from the variable."
  [variable]
  (get-in variable [:umm :Alias]))

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

(defn- get-rate
  "Gets the :Rate value for compression-format in avg-comp-info."
  [avg-comp-info compression-format]
  (some #(when (= compression-format (:Format %)) (:Rate %)) avg-comp-info))  

(defn- get-avg-compression-rate
  "Gets :Rate value for compression-format in :SizeEstimation of variable 
  and parses it to a number."
  [variable compression-format]
  (-> variable
      (get-in [:umm :SizeEstimation :AverageCompressionInformation])
      (get-rate compression-format)
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

(defn- estimate-tabular-ascii-size-per-granule
  "Calculate the estimated size for Tabular ASCII output per granule, for the variable.
  In Tabular ASCII output, the first row is all the aliases separated by a space(or newline in the end),
  Then all the data under each alias are padded to contain the same size as the alias.
  
  size = (# of characters in the alias + 1(space or newline)) * (dimensionality + 1(the first row))
  
  Note: We have made three assumptions in this approach, which needs to be verified:
  1. Different variable may have different dimensionality, but tabular ascii only groups
  the variables with the same dimensionalities together, if not we will need to use the max dimensionality.
  2. Data width will never be wider than the header width. 
  3. Alias put in the variable is the same as what appears in the tabular ascii - confirmed"
  [variable params total-estimate]
  (let [alias (get-alias variable)
        dimensionality (get-dimensionality variable)]
    (log/info (format (str "request-id: %s variable-id: %s total-estimate: %s "
                           "dimensionality: %s variable alias: %s")
                      (:request-id params) (get-in variable [:meta :concept-id])
                      total-estimate dimensionality alias))
    (if (and alias (> dimensionality 0))
      (* (inc (count alias)) (inc dimensionality))
      0)))

(defn- estimate-tabular-ascii-size
  "Calculates the estimated size for Tabular ASCII output for all the variables and the granule-count."
  [granule-count variables params]
  (* granule-count
     (long (reduce (fn [total-estimate variable]
                     (+ total-estimate
                        (estimate-tabular-ascii-size-per-granule variable params total-estimate)))
                   0
                   variables))))

(defn- estimate-size-with-avg-compression-rate
  "Calculates the estimated size for NETCDF4 and shapefile format.
   total-granule-input-bytes is a value given by the client in the size estimate request."
  [granule-count variables params compression-format]
  (reduce (fn [total-estimate variable]
            (let [total-granule-input-bytes (read-string (:total-granule-input-bytes params))
                  avg-compression-rate (get-avg-compression-rate variable compression-format)]
              (log/info (format (str "request-id: %s variable-id: %s total-estimate: %s "
                                     "total-granule-input-bytes: %s "
                                     "avg-compression-rate: %s")
                                (:request-id params) (get-in variable [:meta :concept-id])
                                total-estimate total-granule-input-bytes
                                avg-compression-rate))
              (+ total-estimate
                 (* total-granule-input-bytes
                    (or avg-compression-rate 0)))))
          0
          variables))

(defn- not-implemented-msg
  "Generate the msg for the format that's not implemented yet for the service type."
  [fmt svc-type]
  (format "[%s] format is not implemented yet for service type: [%s]." fmt svc-type))

(defn- not-supported-format-for-service-type-msg
  "Generate the msg about fmt not being supported for the service type."
  [fmt svc-type]
  (format "Cannot estimate size for service type: [%s] and format: [%s]."
          svc-type
          fmt))

(defn- not-supported-service-type-msg
  "Generate the msg about not having the right service type for the service id."
  [svc-id]
  (format "No esi or opendap service type associated with: [%s]." svc-id))

(defn estimate-size
  [svcs granule-count vars granule-metadata-size params]
  (let [svc-id (:service-id params)
        svc-type (if svc-id
                   ;; There is at most one svc associated with svc-id 
                   (get-in (first svcs) [:umm :Type])              
                   opendap-service-type)
        svc-type-lc (when svc-type
                      (string/lower-case svc-type))
        format (:format params)
        fmt-lc (if format 
                 (string/lower-case format)
                 default-format)
        ses-fmt (get-in service-type->formats-mapping [svc-type-lc fmt-lc])]
    (if (or (= svc-type-lc opendap-service-type)
            (= svc-type-lc egi-service-type))
      (case (keyword ses-fmt)
        :dods (estimate-dods-size granule-count vars params)
        :nc (estimate-netcdf3-size granule-count vars granule-metadata-size params)
        (or :nc4 :shapefile :native) 
          (estimate-size-with-avg-compression-rate 
            granule-count vars params (get ses-formats->compression-format-mapping ses-fmt))
        :ascii (estimate-ascii-size granule-count vars params)
        :tabular_ascii (estimate-tabular-ascii-size granule-count vars params) 
        :geotiff
          {:errors [(not-implemented-msg ses-fmt svc-type)]}
        (do
          (let [message (not-supported-format-for-service-type-msg format svc-type)]
            (log/errorf message)
            {:errors [message]})))
      (do
        (let [message (not-supported-service-type-msg svc-id)]
          (log/errorf message)
          {:errors [message]})))))
