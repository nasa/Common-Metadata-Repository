(ns cmr.umm-spec.migration.service-service-options
  "Contains functions for migrating between versions of the UMM Service schema
   for service options."
  (:require
   [clojure.set :as set]
   [cmr.common.util :refer [remove-nil-keys remove-empty-maps]]))

(defn- duplicate-supported-projections
  "Duplicate the SupportedProjections in v1.1 ServiceOptions to SupportedInputProjections
  and SupportedOutputProjections in v1.2 ServiceOptions when migrate from 1.1 to 1.2."
  [service-options]
  (-> service-options
      (assoc :SupportedInputProjections (seq (map #(hash-map :ProjectionName %)
                                                  (:SupportedProjections service-options))))
      (assoc :SupportedOutputProjections (seq (map #(hash-map :ProjectionName %)
                                                   (:SupportedProjections service-options))))
      (dissoc :SupportedProjections)))

(defn- duplicate-supported-formats
  "Duplicate the SupportedFormats in v1.1 ServiceOptions to SupportedInputFormats
  and SupportedOutputFormats in v1.2 ServiceOptions when migrate from 1.1 to 1.2."
  [service-options]
  (-> service-options
      (assoc :SupportedInputFormats (:SupportedFormats service-options))
      (assoc :SupportedOutputFormats (:SupportedFormats service-options))
      (dissoc :SupportedFormats)))

(def v1-1-supported-formats-enum->v1-2-supported-formats-enum
  "Defines SupportedFormats ENUM changes from v1.1 to v1.2"
  {"HDF4" "HDF4"
   "HDF5" "HDF5"
   "HDF-EOS4" "HDF-EOS2"
   "HDF-EOS5" "HDF-EOS5"
   "netCDF-3" "NETCDF-3"
   "netCDF-4" "NETCDF-4"
   "Binary" "BINARY"
   "ASCII" "ASCII"
   "PNG" "PNG"
   "JPEG" "JPEG"
   "GeoTIFF" "GEOTIFF"
   "image/png" "PNG"
   "image/tiff" "TIFF"
   "image/gif" "GIF"
   "image/png; mode=24bit" "PNG24"
   "image/jpeg" "JPEG"
   "image/vnd.wap.wbmp" "BMP"})

(def v1-2-supported-formats-enum->v1-1-supported-formats-enum
  "Defines SupportedFormats ENUM changes from v1.2 to v1.1"
  (-> v1-1-supported-formats-enum->v1-2-supported-formats-enum
      set/map-invert
      (assoc "HDF-EOS" "HDF-EOS5")))

(defn- supported-Formats-1-1->1-2
  "Migrate SupportedFormats from vector of 1.1 enums to vector of 1.2 enums."
  [supported-formats]
  (let [supported-formats (->> supported-formats
                               (map #(get v1-1-supported-formats-enum->v1-2-supported-formats-enum %))
                               (remove nil?))]
    (when (seq supported-formats)
      (vec supported-formats))))

(defn- v1-1-supported-formats->v1-2-supported-formats
  "Migrate v1.1 supported formats in ServiceOptions to v1.2"
  [service-options]
  (-> service-options
      (update :SupportedFormats supported-Formats-1-1->1-2)
      duplicate-supported-formats))

(defn- revert-supported-projections
  "Revert the SupportedInputProjections and SupportedOutputProjections in v1.2 ServiceOptions
  to SupportedProjections in v1.1 ServiceOptions when migrate from 1.2 to 1.1."
  [service-options]
  (-> service-options
      (assoc :SupportedProjections (seq (map :ProjectionName
                                             (:SupportedInputProjections service-options))))
      (dissoc :SupportedInputProjections :SupportedOutputProjections)))

(defn- supported-Formats-1-2->1-1
  "Migrate SupportedFormats from vector of 1.2 enums to vector of 1.1 enums."
  [supported-formats]
  (let [supported-formats (->> supported-formats
                               (map #(get v1-2-supported-formats-enum->v1-1-supported-formats-enum %))
                               (remove nil?))]
    (when (seq supported-formats)
      (vec supported-formats))))

(defn- revert-supported-formats
  "Revert the SupportedInputFormats and SupportedOutputFormats in v1.2 ServiceOptions
  to SupportedFormats in v1.1 ServiceOptions when migrate from 1.2 to 1.1."
  [service-options]
  (-> service-options
      (assoc :SupportedFormats (:SupportedInputFormats service-options))
      (dissoc :SupportedInputFormats :SupportedOutputFormats)))

(defn- v1-2-supported-formats->v1-1-supported-formats
  "Migrate v1.2 supported formats in ServiceOptions to v1.1"
  [service-options]
  (-> service-options
      revert-supported-formats
      (update :SupportedFormats supported-Formats-1-2->1-1)))

(defn v1-1-service-options->v1-2-service-options
  "Migrate v1.1 ServiceOptions to v1.2"
  [service-options]
  (-> service-options
      duplicate-supported-projections
      v1-1-supported-formats->v1-2-supported-formats))

(defn v1-2-service-options->v1-1-service-options
  "Migrate v1.2 ServiceOptions to v1.1"
  [service-options]
  (-> service-options
      revert-supported-projections
      v1-2-supported-formats->v1-1-supported-formats
      (dissoc :VariableAggregationSupportedMethods :MaxGranules)))

(def CRSIdentifierTypeEnum-service-1_2
  "These are the valid enum values in UMM-S 1.2. These are used to migrate ServiceOptions
   SupportedInputProjections/SupportedOutputProjections ProjectionAuthority string in version 1.3 to 1.2."
  ["4326", "3395", "3785", "9807", "2000.63", "2163", "3408", "3410", "6931",
   "6933", "3411", "9822", "54003", "54004", "54008", "54009", "26917", "900913"])

(defn check-projection-valid-values
  "For a projection check to see if the ProjectionAuthority is a valid value for UMM-S 1.2.
   If it is then use as is, if not them remove the invalid value. Return a Valid UMM-S version
   1.2 SupportedProjectionType object."
  [projection]
  (if (some #(= (:ProjectionAuthority projection) %) CRSIdentifierTypeEnum-service-1_2)
    projection
    (dissoc projection :ProjectionAuthority)))

(defn remove-non-valid-formats
  "Remove the formats that are not valid for UMM-S version 1.2."
  [formats]
  (remove #(= "ZARR" %) formats))

(defn update-projections
  "Iterate through each projection calling the check-projection-valid-values function
   checking to see if the ProjectionAuthority is valid. Return a list of Valid UMM-S version 1.2
   SupportedProjectionType objects."
  [projections]
  (map #(check-projection-valid-values %) projections))

(defn update-service-options-1_3->1_2
  "Update the service options from the passed in 1.3 UMM-S record to a valid UMM-S version 1.2 record."
  [s]
  (-> s
      (update-in [:ServiceOptions :SupportedInputProjections] update-projections)
      (update-in [:ServiceOptions :SupportedOutputProjections] update-projections)
      (update :ServiceOptions dissoc :SupportedReformattings)
      (update-in [:ServiceOptions :SupportedInputFormats] remove-non-valid-formats)
      (update-in [:ServiceOptions :SupportedOutputFormats] remove-non-valid-formats)))

(defn create-supported-reformatting-for-1-3
  "This function takes the format input and output and creates a single SupportedReformattingsPairType."
  [input-format output-format]
  {:SupportedInputFormat input-format
   :SupportedOutputFormat output-format})

(defn update-supported-reformatting-for-1-3
  "This function takes a 1.3.1 SupportedReformattingsPairType and iterates through the SupportedOutputFormats
   list creating single input and output format pairs for UMM-S version 1.3 SupportedReformattingsPairType."
  [supported-reformatting]
  (let [input-format (:SupportedInputFormat supported-reformatting)]
    (map #(create-supported-reformatting-for-1-3 input-format %) (:SupportedOutputFormats supported-reformatting))))

(defn update-supported-reformattings-for-1-3
  "Iterate through the passed in supported reformattings and update each one."
  [supported-reformattings]
  (mapcat #(update-supported-reformatting-for-1-3 %) supported-reformattings))

(defn update-service-options-1_3_1->1_3
  "Update the service options from the passed in 1.3.1 UMM-S record to a valid UMM-S version 1.3 record."
  [s]
  (update-in s [:ServiceOptions :SupportedReformattings] update-supported-reformattings-for-1-3))

(defn get-supported-output-formats
  "The input is a vector of maps. Example: [{:SupportedInputFormat HDF5
                                             :SupportedOutputFormat H1}
                                            {:SupportedInputFormat HDF5
                                             :SupportedOutputFormat H2}
   Return a vector of values from the SupportedOutputFormat key. Example: [H1 H2]"
  [vector-of-maps]
  (into [] (map :SupportedOutputFormat vector-of-maps)))

(defn build-supported-reformattings-pair-for-1-3-1
  "Build a list of version 1.3.1 SupportedReformattingsPairType. The input is a vector that contains
   2 items. The first is the input format and the second is a vector of version 1.3
   SupportedReformattingsPairTypes.  (Ex. {HDF5 [{:SupportedInputFormat HDF5,
                                                  :SupportedOutputFormat H1}
                                                 {:SupportedInputFormat HDF5,
                                                  :SupportedOutputFormat H2})."
  [vector-of-a-group]
  {:SupportedInputFormat (first vector-of-a-group)
   :SupportedOutputFormats (get-supported-output-formats (second vector-of-a-group))})

(defn update-supported-reformattings-for-1-3-1
  "Iterate through each supported-reformatting pairs to migrate the SupportedOutputFormat to
   SupportedOutputFormats."
  [supported-reformattings]
  (let [group (group-by :SupportedInputFormat supported-reformattings)]
    (into [] (map #(build-supported-reformattings-pair-for-1-3-1 %) group))))

(defn update-service-options-1_3->1_3_1
  "Update the service options from the passed in 1.3 UMM-S record to a valid UMM-S version 1.3.1 record."
  [s]
  (update-in s [:ServiceOptions :SupportedReformattings] #(update-supported-reformattings-for-1-3-1 %)))

(defn remove-non-valid-formats-1_3_3-to-1_3_2
  "Remove the non valid Supported Format enumerations when migrating to 1.3.2 from 1.3.3."
  [supported-formats]
  (->> supported-formats
       (remove #(= "Shapefile" %))
       (remove #(= "GeoJSON" %))
       (remove #(= "COG" %))
       (remove #(= "WKT" %))
       seq))

(defn remove-reformattings-when-input-not-valid
  [reformatting]
  (let [input (remove-non-valid-formats-1_3_3-to-1_3_2 (vector (:SupportedInputFormat reformatting)))]
    (when input
      (update reformatting :SupportedOutputFormats remove-non-valid-formats-1_3_3-to-1_3_2))))

(defn remove-reformattings-non-valid-formats
  "Remove the non valid formats going from UMM-S version 1.3.3 to UMM-S version 1.3.2"
  [reformattings]
  (remove nil? (map #(remove-reformattings-when-input-not-valid %) reformattings)))

(defn- create-spatial-subset-type-1_3_4
  "Create a spatial subset map as per UMM-S version 1.3.4 depending on the passed in service type.
   Looking at the UMM-S records that exist in OPS and UAT, we can migrate existing UMM-S records
   to this new spatial subset structure. Pass back the spatial subset map depending on this type
   or nil if it is determined that spatial is not supported."
  [service-type]
  (case service-type
        ("OPeNDAP"
         "THREDDS") {:BoundingBox {:AllowMultipleValues false}}
        ("ESI"
         "ECHO ORDERS") {:BoundingBox {:AllowMultipleValues false}
                         :Shapefile [{:Format "ESRI"}
                                     {:Format "KML"}
                                     {:Format "GeoJSON"}]}
        nil))

(defn- create-variable-subset-type-1_3_4
  "Create a variable subset map as per UMM-S version 1.3.4 depending on the passed in service type.
   Looking at the UMM-S records that exist in OPS and UAT, we can migrate existing UMM-S records
   to this new variable subset structure. Pass back the variable subset map depending on this type."
  [service-type]
  (case service-type
        ("OPeNDAP"
         "ECHO ORDERS"
         "ESI"
         "THREDDS") {:AllowMultipleValues true}
        {:AllowMultipleValues false}))

(defn- create-subset-type-1_3_4
  "Create a variable, temporal, or spatial map for the subset element as per UMM-S version 1.3.4
   depending on the passed in subset type. The passed in service-type will be used to determine what
   sub elements and values will be used. A map for variable, temporal, or spatial data is passed back."
  [subset-type service-type]
  (case subset-type
        "Variable" {:VariableSubset (create-variable-subset-type-1_3_4 service-type)}
        "Temporal" {:TemporalSubset {:AllowMultipleValues false}}
        "Spatial" {:SpatialSubset (create-spatial-subset-type-1_3_4 service-type)}
        nil))

(defn create-subset-type-1_3_3-to-1_3_4
  "Create the UMM-S version 1.3.4 subset element using the service type to make choices of which
   sub elements to use.  A full map that represents the subset structure is passed back."
  [s]
  (let [service-type (:Type s)
        subset-types (distinct
                       (get-in s [:ServiceOptions :SubsetTypes]))]
    (->> subset-types
         (map #(create-subset-type-1_3_4 % service-type))
         (into {})
         (remove-nil-keys)
         (remove-empty-maps))))

(defn find-first-supported-reformatting
  "Look through the passed in collection which contains the supported reformatings and return the
   index of the array of the first occurence where the SupportedInputFormat matches the passed in
   input-format."
  [input-format coll]
  (first
    (keep-indexed #(when (= (:SupportedInputFormat %2) input-format) %1) coll)))

(defn move-supported-formats-to-reformattings-for-1_3_4
  "Take the passed in input-formats and output-formats and convert the data
   to the passed in supported-reformattings. Any duplicate data in the
   output-formats sub element is omitted."
  [supported-reformattings input-formats output-formats]
  (let [supported-reformattings (when (seq supported-reformattings)
                                  (into [] supported-reformattings))
        join-formats (fn [sup-ref input-format]
                       (let [sup-ref (if (seq sup-ref)
                                       sup-ref
                                       [])
                             index (find-first-supported-reformatting input-format sup-ref)
                             struct (get sup-ref index)]
                         (if index
                           (assoc sup-ref index
                                  (update struct :SupportedOutputFormats
                                          #(into [] (distinct (concat % output-formats)))))
                           (assoc sup-ref (count sup-ref) {:SupportedInputFormat input-format
                                                           :SupportedOutputFormats output-formats}))))]
    (reduce join-formats supported-reformattings input-formats)))

(defn create-subset-type-1_3_4-to-1_3_3
  "Using the passed in UMM-S version 1.3.4 subset structure, build a vector for UMM-S version 1.3.3
   SubsetTypes"
  [subset]
  (let [create-subset-type
         (fn [subset-type-vector subset-type]
           (case subset-type
                 :SpatialSubset (conj subset-type-vector "Spatial")
                 :TemporalSubset (conj subset-type-vector "Temporal")
                 :VariableSubset (conj subset-type-vector "Variable")))]
    (reduce create-subset-type [] (keys subset))))

(defn- replace-non-valid-formats-1_3_4-to-1_3_3
  "Find and replace the non valid Supported Format enumerations when migrating from 1.3.4 to 1.3.3."
  [supported-format-vector]
  (if (some #(= "Shapefile+zip" %) supported-format-vector)
    (let [supported-format-vector (->> supported-format-vector
                                       (remove #(= "Shapefile+zip" %))
                                       (into []))]
      (into []
        (-> supported-format-vector
            (assoc (count supported-format-vector) "Shapefile")
            (distinct))))
    supported-format-vector))

(defn- remove-reformattings-when-input-not-valid-1_3_4-to-1_3_3
  "Get the replaced vector values and place them in the proper place in the
   supported reformatting map."
  [reformatting]
  (when-let [input (replace-non-valid-formats-1_3_4-to-1_3_3 (vector (:SupportedInputFormat reformatting)))]
    (-> reformatting
        (assoc :SupportedInputFormat (first input))
        (update :SupportedOutputFormats replace-non-valid-formats-1_3_4-to-1_3_3))))

(defn remove-reformattings-non-valid-formats-1_3_4-to-1_3_3
  "Replace the non valid formats going from UMM-S version 1.3.4 to UMM-S version 1.3.3"
  [reformattings]
  (remove nil? (map #(remove-reformattings-when-input-not-valid-1_3_4-to-1_3_3 %) reformattings)))
