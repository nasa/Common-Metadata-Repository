(ns cmr.umm-spec.test.service-schema-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [cmr.umm-spec.json-schema :as json-schema]))

(deftest service-supported-format-type-enum-test
  (testing "Validation of UMM-S with NETCDF-4 (OPeNDAP URL) format (CMR-11048)"
    (let [valid-service-json (slurp (io/resource "example-data/umm-json/service/v1.5.4/Service_v1.5.4.json"))
          validation-errors (json-schema/validate-umm-json valid-service-json :service "1.5.4")]
      (is (empty? validation-errors) (str "Validation errors: " (pr-str validation-errors)))))

  (testing "Validation of UMM-S 1.5.3 example file"
    (let [json (slurp (io/resource "example-data/umm-json/service/v1.5.3/Service_v1.5.3.json"))
          validation-errors (json-schema/validate-umm-json json :service "1.5.3")]
      (is (empty? validation-errors) (str "Validation errors: " (pr-str validation-errors))))))
