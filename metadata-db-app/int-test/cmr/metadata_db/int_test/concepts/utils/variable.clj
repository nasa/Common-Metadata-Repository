(ns cmr.metadata-db.int-test.concepts.utils.variable
  "Defines implementations for all of the multi-methods for variables in the metadata-db
  integration tests."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def variable-json
  (json/generate-string
    {"Name" "totCldH2OStdErr",
     "LongName" "totCldH2OStdErr",
     "Units" "MB",
     "DataType" "float",
     "Dimensions" [ {"Name" "Solution_3_Land", "Size" 3, "Type" "OTHER"}
                    {"Name" "YDim" "Size" 180.0 "Type" "LATITUDE_DIMENSION"}],
     "Sets" [{ "Name" "/", "Type" "General", "Size" 1.0, "Index" 1.0 }],
     "Scale" "1.0",
     "Offset" "0.0",
     "FillValue" "-9999.0 ",
     "VariableType" "",
     "ScienceKeywords" []
     "AcquisitionSourceName" "Instrument1"}))

(defmethod concepts/get-sample-metadata :variable
  [_]
  variable-json)

(defn- create-variable-concept
  "Creates a variable concept"
  [provider-id uniq-num attributes]
  (let [native-id (str "var-native" uniq-num)
        extra-fields (merge {:variable-name (str "var" uniq-num)
                             :measurement (str "measurement" uniq-num)
                             :fingerprint (str "fingerprint" uniq-num)}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id :variable uniq-num attributes)))

(defmethod concepts/create-concept :variable
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :variable args)]
    (create-variable-concept provider-id uniq-num attributes)))
