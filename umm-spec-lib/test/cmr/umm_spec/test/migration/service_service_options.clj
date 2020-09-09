(ns cmr.umm-spec.test.migration.service-service-options
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.migration.service-service-options :as service]))

(deftest update-supported-reformattings-for-1-3-1-test
  "Test to see that the migration of the SupportedReformattingsPairType from 1.3 to 1.3.1 works."

  (let [supported-reformattings [{:SupportedInputFormat "HDF5", :SupportedOutputFormat "H1"}
                                 {:SupportedInputFormat "HDF5", :SupportedOutputFormat "H2"}
                                 {:SupportedInputFormat "HDF5", :SupportedOutputFormat "H3"}
                                 {:SupportedInputFormat "HDF6", :SupportedOutputFormat "H1"}
                                 {:SupportedInputFormat "HDF6", :SupportedOutputFormat "H2"}
                                 {:SupportedInputFormat "HDF6", :SupportedOutputFormat "H3"}
                                 {:SupportedInputFormat "HDF5", :SupportedOutputFormat "H4"}]]

    (is (= [{:SupportedInputFormat "HDF5",
             :SupportedOutputFormats ["H1" "H2" "H3" "H4"]}
            {:SupportedInputFormat "HDF6",
             :SupportedOutputFormats ["H1" "H2" "H3"]}]
           (service/update-supported-reformattings-for-1-3-1 supported-reformattings)))))

(deftest update-supported-reformattings-for-1-3-test
  "Test to see that the migration of the SupportedReformattingsPairType from 1.3.1 to 1.3 works."

  (let [supported-reformattings [{:SupportedInputFormat "HDF5"
                                  :SupportedOutputFormats ["H1" "H2" "H3"]}
                                 {:SupportedInputFormat "HDF6"
                                  :SupportedOutputFormats ["H1" "H2" "H3"]}
                                 {:SupportedInputFormat "HDF5"
                                  :SupportedOutputFormats ["H4"]}]]
    (is (= '({:SupportedInputFormat "HDF5" :SupportedOutputFormat "H1"}
             {:SupportedInputFormat "HDF5" :SupportedOutputFormat "H2"}
             {:SupportedInputFormat "HDF5" :SupportedOutputFormat "H3"}
             {:SupportedInputFormat "HDF6" :SupportedOutputFormat "H1"}
             {:SupportedInputFormat "HDF6" :SupportedOutputFormat "H2"}
             {:SupportedInputFormat "HDF6" :SupportedOutputFormat "H3"}
             {:SupportedInputFormat "HDF5" :SupportedOutputFormat "H4"})
           (service/update-supported-reformattings-for-1-3 supported-reformattings)))))

(deftest update-subset-type-1-3-3-to-1-3-4-test
  "Testing to see if umm-s version 1.3.3 is converted to 1.3.4 for ServiceOptions."
  (are3 [s1-3-3 s1-3-4]
    (is (= s1-3-4
           (service/create-subset-type-1_3_3-to-1_3_4 s1-3-3)))

    "Converting ESI type spatial subset"
    {:Type "ESI"
     :ServiceOptions {:SubsetTypes ["Spatial", "Temporal", "Variable"]}}
    {:SpatialSubset
      {:BoundingBox {:AllowMultipleValues false}
       :Shapefile [{:Format "ESRI"} {:Format "KML"} {:Format "GeoJSON"}]}
     :TemporalSubset {:AllowMultipleValues false}
     :VariableSubset {:AllowMultipleValues true}}

    "Converting NOT PROVIDED type spatial subset"
    {:Type "NOT PROVIDED"
     :ServiceOptions {:SubsetTypes ["Spatial", "Temporal", "Variable"]}}
    {:TemporalSubset {:AllowMultipleValues false}
     :VariableSubset {:AllowMultipleValues false}}

    "Converting THREDDS type spatial subset"
    {:Type "THREDDS"
     :ServiceOptions {:SubsetTypes ["Spatial", "Temporal", "Variable"]}}
    {:SpatialSubset
      {:BoundingBox {:AllowMultipleValues false}}
     :TemporalSubset {:AllowMultipleValues false}
     :VariableSubset {:AllowMultipleValues true}}

    "Converting THREDDS type with no Spatial subset"
    {:Type "THREDDS"}
    nil))

(deftest move-supported-formats-to-reformattings-for-1-3-4-test
  "Test to see if umm-s version 1.3.3 SupportedInputFormats and SupportedOutputFormats are converted
   SupportedReformattings."
  (are3 [service-options-1-3-3 service-options-1-3-4]
    (is (= service-options-1-3-4
           (service/move-supported-formats-to-reformattings-for-1_3_4
             (:SupportedReformattings service-options-1-3-3)
             (:SupportedInputFormats service-options-1-3-3)
             (:SupportedOutputFormats service-options-1-3-3))))

    "Converting SupportedInput/OutputFormats to SupportedReformattings when SupportedReformattings
     is nil."
    {:SupportedInputFormats ["HDF4", "Shapefile"]
     :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}
     {:SupportedInputFormat "Shapefile"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}]

    "Converting SupportedInput/OutputFormats to SupportedReformattings when SupportedReformattings
     is populated with SupportedInputFormats that are not equal to the passed in SupportedInputFormats."
    {:SupportedInputFormats ["HDF4", "Shapefile"]
     :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]
     :SupportedReformattings [{:SupportedInputFormat "HDF5"
                               :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]}
    [{:SupportedInputFormat "HDF5"
      :SupportedOutputFormats ["GEOTIFFFLOAT32"]}
     {:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}
     {:SupportedInputFormat "Shapefile"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}]

    "Converting SupportedInput/OutputFormats to SupportedReformattings when SupportedReformattings
     is populated with Data that contains a value that is already included."
    {:SupportedInputFormats ["HDF4", "Shapefile"]
     :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]
     :SupportedReformattings [{:SupportedInputFormat "HDF4"
                               :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]}
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["GEOTIFFFLOAT32" "HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}
     {:SupportedInputFormat "Shapefile"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}]

    "Testing empty input/ouput formats."
    {:SupportedInputFormats []
     :SupportedOutputFormats []
     :SupportedReformattings [{:SupportedInputFormat "HDF4"
                               :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]}
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]

    "Testing empty output formats and nil input formats."
    {:SupportedOutputFormats []
     :SupportedReformattings [{:SupportedInputFormat "HDF4"
                               :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]}
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]

    "Testing empty input formats and nil output formats."
    {:SupportedInputFormats []
     :SupportedReformattings [{:SupportedInputFormat "HDF4"
                               :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]}
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]

    "Testing nil input and output formats."
    {:SupportedInputFormats nil
     :SupportedReformattings [{:SupportedInputFormat "HDF4"
                               :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]}
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["GEOTIFFFLOAT32"]}]

    "Testing nil input formats and supported reformattings."
    {:SupportedOutputFormats ["GEOTIFFFLOAT32"]}
    nil

    "Tested with SupportedReformattings which is an empty list."
    {:SupportedInputFormats [ "ASCII" "GEOTIFF" "NETCDF-3"]
     :SupportedOutputFormats [ "ASCII" "GEOTIFF" "NETCDF-3"]
     :SupportedReformattings '()}
    [{:SupportedInputFormat "ASCII",
      :SupportedOutputFormats ["ASCII" "GEOTIFF" "NETCDF-3"]}
     {:SupportedInputFormat "GEOTIFF",
      :SupportedOutputFormats ["ASCII" "GEOTIFF" "NETCDF-3"]}
     {:SupportedInputFormat "NETCDF-3",
      :SupportedOutputFormats ["ASCII" "GEOTIFF" "NETCDF-3"]}]

    "Testing nil inputs."
    nil
    nil

    "Testing with SupportedReformattings as a set instead of a vector"
    {:SupportedInputFormats ["GEOTIFF" "NETCDF-3"]
     :SupportedOutputFormats ["GEOTIFF" "NETCDF-3"]
     :SupportedReformattings '({:SupportedInputFormat "ASCII"
                                :SupportedOutputFormats ["ASCII" "GEOTIFF" "NETCDF-3"]})}
    [{:SupportedInputFormat "ASCII",
      :SupportedOutputFormats ["ASCII" "GEOTIFF" "NETCDF-3"]}
     {:SupportedInputFormat "GEOTIFF",
      :SupportedOutputFormats ["GEOTIFF" "NETCDF-3"]}
     {:SupportedInputFormat "NETCDF-3",
      :SupportedOutputFormats ["GEOTIFF" "NETCDF-3"]}]))

(deftest create-subset-type-1-3-4-to-1-3-3-test
  "Test to see if umm-s version 1.3.3 SupportedInputFormats and SupportedOutputFormats are converted
   SupportedReformattings."
  (are3 [subset expected-subset-type]
    (is (= expected-subset-type
           (service/create-subset-type-1_3_4-to-1_3_3 subset)))

    "Testing that spatial temporal and variable are all accounted for."
    {:SpatialSubset {:Point {:AllowMultipleValues true}}
     :TemporalSubset {:AllowMultipleValues false}
     :VariableSubset {:AllowMultipleValues false}}
    ["Spatial" "Temporal" "Variable"]

    "Testing that spatial and variable are accounted for."
    {:SpatialSubset {:Point {:AllowMultipleValues true}}
     :VariableSubset {:AllowMultipleValues false}}
    ["Spatial" "Variable"]

    "Testing that spatial and variable are accounted for."
    nil
    []))

(deftest remove-reformattings-non-valid-formats-1-3-4-to-1-3-3-test
  "Test the replacement of non valid Supported Formats when migrating from UMM-S 1.3.4 to 1.3.3."
  (are3 [supported-reformattings expected-supported-reformattings]
    (is (= expected-supported-reformattings
           (service/remove-reformattings-non-valid-formats-1_3_4-to-1_3_3 supported-reformattings)))

    "Testing the replacement in Supported Output Formats."
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["Shapefile+zip" "HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}
     {:SupportedInputFormat "Shapefile"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","Shapefile+zip", "ZARR", "GeoJSON"]}]
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON", "Shapefile"]}
     {:SupportedInputFormat "Shapefile"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY", "ZARR", "GeoJSON", "Shapefile"]}]

    "Testing the replacement in Supported Input Formats."
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["Shapefile+zip" "HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON"]}
     {:SupportedInputFormat "Shapefile+zip"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY", "ZARR", "GeoJSON", "Shapefile+zip"]}]
    [{:SupportedInputFormat "HDF4"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY","ASCII", "ZARR", "GeoJSON", "Shapefile"]}
     {:SupportedInputFormat "Shapefile"
      :SupportedOutputFormats ["HDF4","NETCDF-3","NETCDF-4","BINARY", "ZARR", "GeoJSON", "Shapefile"]}]))
