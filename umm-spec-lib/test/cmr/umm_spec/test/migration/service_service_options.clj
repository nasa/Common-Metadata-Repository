(ns cmr.umm-spec.test.migration.service-service-options
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.migration.service-service-options :as service]))

;; Test to see that the migration of the SupportedReformattingsPairType from 1.3 to 1.3.1 works.
(deftest update-supported-reformattings-for-1-3-1-test

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

;; Test to see that the migration of the SupportedReformattingsPairType from 1.3.1 to 1.3 works.
(deftest update-supported-reformattings-for-1-3-test

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

;; Testing to see if umm-s version 1.3.3 is converted to 1.3.4 for ServiceOptions.
(declare s1-3-3 s1-3-4)
(deftest update-subset-type-1-3-3-to-1-3-4-test
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

;; Test to see if umm-s version 1.3.3 SupportedInputFormats and SupportedOutputFormats are converted SupportedReformattings.
(declare service-options-1-3-3 service-options-1-3-4)
(deftest move-supported-formats-to-reformattings-for-1-3-4-test
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

;; Test to see if umm-s version 1.3.3 SupportedInputFormats and SupportedOutputFormats are converted SupportedReformattings.
(declare subset expected-subset-type)
(deftest create-subset-type-1-3-4-to-1-3-3-test
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

;; Test the replacement of non valid Supported Formats when migrating from UMM-S 1.3.4 to 1.3.3.
(declare supported-reformattings expected-supported-reformattings)
(deftest remove-reformattings-non-valid-formats-1-3-4-to-1-3-3-test
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

(deftest remove-reformattings-non-valid-formats-1-5-4-to-1-5-3-test
  (testing "CMR-11048: Test 1.5.4 -> 1.5.3 migration removes NETCDF-4 (OPeNDAP URL)"
    (are3 [supported-reformattings expected-supported-reformattings]
          (is (= expected-supported-reformattings
                 (service/remove-reformattings-non-valid-formats-1_5_4-to-1_5_3 supported-reformattings)))

          "Testing the removal in Supported Output Formats."
          [{:SupportedInputFormat "HDF4"
            :SupportedOutputFormats ["NETCDF-4 (OPeNDAP URL)" "HDF4" "NETCDF-3"]}
           {:SupportedInputFormat "Shapefile"
            :SupportedOutputFormats ["NETCDF-4 (OPeNDAP URL)"]}]
          [{:SupportedInputFormat "HDF4"
            :SupportedOutputFormats ["HDF4" "NETCDF-3"]}]

          "Testing the removal in Supported Input Formats."
          [{:SupportedInputFormat "NETCDF-4 (OPeNDAP URL)"
            :SupportedOutputFormats ["HDF4"]}
           {:SupportedInputFormat "Shapefile"
            :SupportedOutputFormats ["NETCDF-4 (OPeNDAP URL)" "HDF4"]}]
          [{:SupportedInputFormat "Shapefile"
            :SupportedOutputFormats ["HDF4"]}]
          
           "Testing removal of pair when outputs become empty"
           [{:SupportedInputFormat "HDF4"
             :SupportedOutputFormats ["NETCDF-4 (OPeNDAP URL)"]}]
           [])))

(deftest remove-non-valid-formats-1-5-4-to-1-5-3-test
  (testing "Removal of NETCDF-4 (OPeNDAP URL) from supported formats list"
    (is (= ["HDF4" "NETCDF-3"]
           (service/remove-non-valid-formats-1_5_4-to-1_5_3
            ["HDF4" "NETCDF-4 (OPeNDAP URL)" "NETCDF-3"])))
    (is (= []
           (service/remove-non-valid-formats-1_5_4-to-1_5_3
            ["NETCDF-4 (OPeNDAP URL)"])))))
