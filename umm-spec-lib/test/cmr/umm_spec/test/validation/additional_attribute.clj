(ns cmr.umm-spec.test.validation.additional-attribute
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.validation.core :as v]
            [cmr.umm-spec.models.common :as c]
            [cmr.umm-spec.models.collection :as coll]
            [cmr.umm-spec.test.validation.helpers :as h]
            [cmr.common.services.errors :as e]))

(defn- coll-with-additional-attributes
  [attributes]
  (coll/map->UMM-C {:AdditionalAttributes (map c/map->AdditionalAttributeType attributes)}))

(deftest collection-additional-attributes-validation
  (testing "additional attributes names"
    (testing "valid additional attributes names"
      (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType "String"}
                                                        {:Name "bar" :DataType "String"}])))
    (testing "invalid additional attributes names"
      (testing "duplicate names"
        (let [coll (coll-with-additional-attributes [{:Name "foo" :DataType "String"}
                                                     {:Name "foo" :DataType "String"}
                                                     {:Name "bar" :DataType "String"}
                                                     {:Name "bar" :DataType "String"}
                                                     {:Name "charlie" :DataType "String"}])]
          (h/assert-invalid
            coll
            [:AdditionalAttributes]
            ["Additional Attributes must be unique. This contains duplicates named [foo, bar]."])))))

  (testing "additional attributes data type"
    (testing "valid data types"
      (are [data-type]
           (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type}]))
           "STRING"
           "FLOAT"
           "INT"
           "BOOLEAN"
           "DATE"
           "TIME"
           "DATETIME"
           "DATE_STRING"
           "TIME_STRING"
           "DATETIME_STRING")))

  (testing "additional attributes values match data type"
    (testing "valid values"
      (are [data-type value]
           (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type :Value value}]))
           "STRING" "string value"
           "FLOAT" "1.0"
           "FLOAT" "1"
           "INT" "1"
           "BOOLEAN" "true"
           "DATE" "1986-10-14"
           "TIME" "04:03:27.123Z"
           "DATETIME" "1986-10-14T04:03:27.0Z"
           "DATE_STRING" "1986-10-14"
           "TIME_STRING" "04:03:27.123"
           "DATETIME_STRING" "1986-10-14T04:03:27.0Z"
           "STRING" nil
           "FLOAT" nil
           "INT" nil
           "BOOLEAN" nil
           "DATE" nil
           "TIME" nil
           "DATETIME" nil
           "DATE_STRING" nil
           "TIME_STRING" nil
           "DATETIME_STRING" nil)
      (are [data-type value]
           (and
             (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type :ParamteterRangeBegin value}]))
             (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type :ParameterRangeEnd value}])))
           "FLOAT" "1.0"
           "INT" "1"
           "DATE" "1986-10-14"
           "TIME" "04:03:27.123Z"
           "DATETIME" "1986-10-14T04:03:27.0Z"
           "DATE_STRING" "1986-10-14"
           "TIME_STRING" "04:03:27.123"
           "DATETIME_STRING" "1986-10-14T04:03:27.0Z"
           "STRING" nil
           "FLOAT" nil
           "INT" nil
           "BOOLEAN" nil
           "DATE" nil
           "TIME" nil
           "DATETIME" nil
           "DATE_STRING" nil
           "TIME_STRING" nil
           "DATETIME_STRING" nil))

    (testing "invalid values"
      (are [data-type value field errors]
           (h/assert-invalid
             (coll-with-additional-attributes [{:Name "foo" :DataType data-type field value}])
             [:AdditionalAttributes 0] errors)

           "BOOLEAN" "true" :ParameterRangeBegin ["Parameter Range Begin is not allowed for type [BOOLEAN]"]
           "FLOAT" "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [FLOAT]."]
           "INT" "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [INT]."]
           "DATE" "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [DATE]."]
           "TIME" "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [TIME]."]
           "DATETIME" "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [DATETIME]."]
           "DATE" "2002-01-01T00:00:00.000Z" :ParameterRangeBegin ["Parameter Range Begin [2002-01-01T00:00:00.000Z] is not a valid value for type [DATE]."]
           "TIME" "2002-01-01T00:00:00.000Z" :ParameterRangeBegin ["Parameter Range Begin [2002-01-01T00:00:00.000Z] is not a valid value for type [TIME]."]
           "DATE" "00:03:01.000" :ParameterRangeBegin ["Parameter Range Begin [00:03:01.000] is not a valid value for type [DATE]."]
           "TIME" "2002-01-01" :ParameterRangeBegin ["Parameter Range Begin [2002-01-01] is not a valid value for type [TIME]."]
           "BOOLEAN" "true" :ParameterRangeEnd ["Parameter Range End is not allowed for type [BOOLEAN]"]
           "FLOAT" "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [FLOAT]."]
           "INT" "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [INT]."]
           "DATE" "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [DATE]."]
           "TIME" "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [TIME]."]
           "DATETIME" "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [DATETIME]."]
           "DATE" "2002-01-01T00:00:00.000Z" :ParameterRangeEnd ["Parameter Range End [2002-01-01T00:00:00.000Z] is not a valid value for type [DATE]."]
           "TIME" "2002-01-01T00:00:00.000Z" :ParameterRangeEnd ["Parameter Range End [2002-01-01T00:00:00.000Z] is not a valid value for type [TIME]."]
           "DATE" "00:03:01.000" :ParameterRangeEnd ["Parameter Range End [00:03:01.000] is not a valid value for type [DATE]."]
           "TIME" "2002-01-01" :ParameterRangeEnd ["Parameter Range End [2002-01-01] is not a valid value for type [TIME]."]
           "BOOLEAN" "bar" :Value ["Value [bar] is not a valid value for type [BOOLEAN]."]
           "FLOAT" "bar" :Value ["Value [bar] is not a valid value for type [FLOAT]."]
           "INT" "bar" :Value ["Value [bar] is not a valid value for type [INT]."]
           "DATE" "bar" :Value ["Value [bar] is not a valid value for type [DATE]."]
           "TIME" "bar" :Value ["Value [bar] is not a valid value for type [TIME]."]
           "DATETIME" "bar" :Value ["Value [bar] is not a valid value for type [DATETIME]."]
           "DATE" "2002-01-01T00:00:00.000Z" :Value ["Value [2002-01-01T00:00:00.000Z] is not a valid value for type [DATE]."]
           "TIME" "2002-01-01T00:00:00.000Z" :Value ["Value [2002-01-01T00:00:00.000Z] is not a valid value for type [TIME]."]
           "DATE" "00:03:01.000" :Value ["Value [00:03:01.000] is not a valid value for type [DATE]."]
           "TIME" "2002-01-01" :Value ["Value [2002-01-01] is not a valid value for type [TIME]."]))

    (testing "multiple invalid values"
      (h/assert-multiple-invalid
        (coll-with-additional-attributes [{:Name "foo" :DataType "FLOAT" :Value "str"}
                                          {:Name "bar" :DataType "FLOAT" :Value "1.0"}
                                          {:Name "baz" :DataType "INT" :Value "1.0"}])
        [{:path [:AdditionalAttributes 0]
          :errors
          ["Value [str] is not a valid value for type [FLOAT]."]}
         {:path [:AdditionalAttributes 2]
          :errors
          ["Value [1.0] is not a valid value for type [INT]."]}]))))
