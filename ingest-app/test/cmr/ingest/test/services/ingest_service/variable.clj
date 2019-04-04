(ns cmr.ingest.test.services.ingest-service.variable
  "This tests variable fingerprint generation."
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.services.ingest-service.variable :as variable]))

(def ^:private variable-metadata-partial
  "Defines variable metadata partial without Dimensions, used to construct variable metadata"
  (str "\"Scale\":1.0,\"Offset\":0.0,"
       "\"Sets\":[{\"Name\":\"Data_Fields\",\"Type\":\"Science\",\"Size\":2,\"Index\":2}],"
       "\"Units\":\"m\",\"FillValues\":[{\"Value\":-9999.0,\"Type\":\"SCIENCE_FILLVALUE\"}],"
       "\"Definition\":\"Defines the variable\",\"AcquisitionSourceName\":\"Instrument1\","
       "\"ScienceKeywords\":[{\"Category\":\"sk-A\",\"Topic\":\"sk-B\",\"Term\":\"sk-C\"}],"
       "\"Name\":\"A-name\",\"VariableType\":\"SCIENCE_VARIABLE\","
       "\"LongName\":\"A long UMM-Var name\",\"DataType\":\"float32\""))

(def ^:private variable-concept
  {:metadata (format "{%s%s}"
                     "\"Dimensions\":[{\"Name\":\"Solution_Land\",\"Size\":3,\"Type\":\"OTHER\"}],"
                     variable-metadata-partial)
   :format "application/vnd.nasa.cmr.umm+json;version=1.5"
   :native-id "A-name"
   :concept-type :variable
   :provider-id "PROV1"})

(def ^:private variable-concept-with-dimension-fields-in-different-order
  {:metadata (format "{%s%s}"
                     "\"Dimensions\":[{\"Type\":\"OTHER\", \"Name\":\"  Solution_Land\",\"Size\":3}],"
                     variable-metadata-partial)
   :format "application/vnd.nasa.cmr.umm+json;version=1.5"
   :native-id "A-name"
   :concept-type :variable
   :provider-id "PROV1"})

(deftest get-variable-fingerprint-test
  (is (= "54ccba05163a514021bfee3a5460909b"
         (#'variable/get-variable-fingerprint variable-concept)
         (#'variable/get-variable-fingerprint
           variable-concept-with-dimension-fields-in-different-order))))
