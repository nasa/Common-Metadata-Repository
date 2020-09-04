(ns cmr.umm-spec.test.fingerprint-util
  "This tests variable fingerprint generation."
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.fingerprint-util :as fingerprint]))

(def ^:private variable-metadata-partial
  "Defines variable metadata partial without Dimensions, used to construct variable metadata"
  (str "\"Scale\":1.0,\"Offset\":0.0,"
       "\"Sets\":[{\"Name\":\"Data_Fields\",\"Type\":\"Science\",\"Size\":2,\"Index\":2}],"
       "\"Units\":\"m\",\"FillValues\":[{\"Value\":-9999.0,\"Type\":\"SCIENCE_FILLVALUE\"}],"
       "\"Definition\":\"Defines the variable\",\"AcquisitionSourceName\":\"Instrument1\","
       "\"ScienceKeywords\":[{\"Category\":\"sk-A\",\"Topic\":\"sk-B\",\"Term\":\"sk-C\"}],"
       "\"Name\":\"A-name\",\"VariableType\":\"SCIENCE_VARIABLE\","
       "\"LongName\":\"A long UMM-Var name\",\"DataType\":\"float32\""))

(def ^:private variable-metadata
  "Defines a sample variable metadata"
  (format "{%s%s}"
          "\"Dimensions\":[{\"Name\":\"Solution_Land\",\"Size\":3,\"Type\":\"OTHER\"}],"
          variable-metadata-partial))

(def ^:private variable-metadata-with-dimension-fields-in-different-order
  "Defines a sample variable metadata with dimension fields in a different order"
  (format "{%s%s}"
          "\"Dimensions\":[{\"Type\":\"OTHER\", \"Name\":\"  Solution_Land\",\"Size\":3}],"
          variable-metadata-partial))

(deftest get-variable-fingerprint-test
  (is (= "698b82b0b96c343e0867dcb62a9e520c"
         (fingerprint/get-variable-fingerprint variable-metadata)
         (fingerprint/get-variable-fingerprint
          variable-metadata-with-dimension-fields-in-different-order))))
