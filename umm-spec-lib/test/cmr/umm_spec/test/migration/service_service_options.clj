(ns cmr.umm-spec.test.migration.service-service-options
  (:require
   [clojure.test :refer :all]
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

    (is (= '({:SupportedInputFormat "HDF5",
              :SupportedOutputFormats ["H1" "H2" "H3" "H4"]}
             {:SupportedInputFormat "HDF6",
               :SupportedOutputFormats ["H1" "H2" "H3"]})
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
