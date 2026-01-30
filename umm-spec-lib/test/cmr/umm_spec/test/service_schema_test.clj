(ns cmr.umm-spec.test.service-schema-test
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.json-schema :as json-schema]))

(deftest service-supported-format-type-enum-test
  (testing "Validation of UMM-S with NETCDF-4 (OPeNDAP URL) format (CMR-11048)"
    (let [valid-service-json (str "{"
                                  "  \"Name\": \"TestValidService\","
                                  "  \"LongName\": \"Long Name Test Valid Service\","
                                  "  \"Type\": \"OPeNDAP\","
                                  "  \"Version\": \"1.0\","
                                  "  \"Description\": \"Description of service\","
                                  "  \"URL\": {"
                                  "    \"URLValue\": \"https://example.com\","
                                  "    \"Description\": \"URL Description\""
                                  "  },"
                                  "  \"ServiceKeywords\": [{"
                                  "    \"ServiceCategory\": \"DATA ANALYSIS AND VISUALIZATION\","
                                  "    \"ServiceTopic\": \"VISUALIZATION/IMAGE PROCESSING\""
                                  "  }],"
                                  "  \"ServiceOrganizations\": [{"
                                  "    \"Roles\": [\"SERVICE PROVIDER\"],"
                                  "    \"ShortName\": \"ESA/ED\"\n"
                                  "  }],"
                                  "  \"MetadataSpecification\": {"
                                  "    \"URL\": \"https://cdn.earthdata.nasa.gov/umm/service/v1.5.4\","
                                  "    \"Name\": \"UMM-S\","
                                  "    \"Version\": \"1.5.4\""
                                  "  },"
                                  "  \"ServiceOptions\": {"
                                  "    \"SupportedReformattings\": [{"
                                  "      \"SupportedInputFormat\": \"NETCDF-4 (OPeNDAP URL)\","
                                  "      \"SupportedOutputFormats\": [\"NETCDF-4 (OPeNDAP URL)\"]"
                                  "    }]"
                                  "  }"
                                  "}")
          validation-errors (json-schema/validate-umm-json valid-service-json :service "1.5.4")]
      (is (empty? validation-errors) (str "Validation errors: " (pr-str validation-errors))))))
