(ns cmr.umm-spec.test.validation.additional-attribute
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.validation.umm-spec-validation-core :as v]
            [cmr.umm-spec.models.umm-common-models :as c]
            [cmr.umm-spec.models.umm-collection-models :as coll]
            [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]
            [cmr.umm-spec.additional-attribute :as aa]
            [cmr.common.services.errors :as e]))

(defn- coll-with-add-attribs
  [attributes]
  (aa/add-parsed-values (coll/map->UMM-C
                         {:AdditionalAttributes
                          (map c/map->AdditionalAttributeType attributes)})))

(deftest collection-additional-attributes-validation
  (testing "additional attributes names"
    (testing "valid additional attributes names"
      (h/assert-valid (coll-with-add-attribs [{:Name "foo" :DataType "STRING"}
                                              {:Name "bar" :DataType "STRING"}])))
    (testing "invalid additional attributes names"
      (testing "duplicate names"
        (let [coll (coll-with-add-attribs [{:Name "foo" :DataType "STRING"}
                                           {:Name "foo" :DataType "STRING"}
                                           {:Name "bar" :DataType "STRING"}
                                           {:Name "bar" :DataType "STRING"}
                                           {:Name "charlie" :DataType "STRING"}])]
          (h/assert-invalid
           coll
           [:AdditionalAttributes]
           ["Additional Attributes must be unique. This contains duplicates named [foo, bar]."])))))

  (testing "additional attributes data type"
    (testing "valid data types"
      (are [data-type]
        (h/assert-valid (coll-with-add-attribs [{:Name "foo" :DataType data-type}]))
        "STRING"
        "FLOAT"
        "INT"
        "BOOLEAN"
        "DATE"
        "TIME"
        "DATETIME"
        "DATE_STRING"
        "TIME_STRING"
        "DATETIME_STRING"))
    (testing "invalid data type"
     (h/assert-invalid (coll-with-add-attribs [{:Name "foo" :DataType "F"}])
      [:AdditionalAttributes 0 :DataType]
      ["Additional Attribute Data Type [F] is not a valid data type."])))

  (testing "additional attributes values match data type"
    (testing "valid values"
      (are [data-type value]
        (h/assert-valid (coll-with-add-attribs [{:Name "foo" :DataType data-type :Value value}]))
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
         (h/assert-valid (coll-with-add-attribs [{:Name "foo" :DataType data-type :ParamteterRangeBegin value}]))
         (h/assert-valid (coll-with-add-attribs [{:Name "foo" :DataType data-type :ParameterRangeEnd value}])))
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
         (coll-with-add-attribs [{:Name "foo" :DataType data-type field value}])
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
       (coll-with-add-attribs [{:Name "foo" :DataType "FLOAT" :Value "str"}
                               {:Name "bar" :DataType "FLOAT" :Value "1.0"}
                               {:Name "baz" :DataType "INT" :Value "1.0"}])
       [{:path [:AdditionalAttributes 0]
         :errors
         ["Value [str] is not a valid value for type [FLOAT]."]}
        {:path [:AdditionalAttributes 2]
         :errors
         ["Value [1.0] is not a valid value for type [INT]."]}]))

    (testing "additional attributes range values"
      (testing "valid range values"
        (are [data-type begin end value]
          (h/assert-valid (coll-with-add-attribs [{:Name "foo"
                                                   :DataType data-type
                                                   :ParameterRangeBegin begin
                                                   :ParameterRangeEnd end
                                                   :Value value}]))
          "STRING" nil nil "string value"
          "FLOAT" "1.0" "3.0" "2.0"
          "INT" "1" "3" "2"
          "INT" "1" "1" "1"
          "BOOLEAN" nil nil "true"
          "DATE" "1986-10-14" "1986-10-16" "1986-10-15"
          "TIME" "04:03:27.123Z" "04:03:29Z" "04:03:28Z"
          "DATETIME" "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:29Z" "1986-10-14T04:03:28Z"
          "DATE_STRING" "1986-10-14" "1986-10-14" "1986-10-14"
          "TIME_STRING" "04:03:27.123" "04:03:27.123" "04:03:27.123"
          "DATETIME_STRING" "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:27.0Z"))

      (testing "invalid range values"
        (testing "parameter range begin is greater than parameter range end"
          (are [data-type begin end value errors]
            (h/assert-invalid
             (coll-with-add-attribs [{:Name "foo"
                                      :DataType data-type
                                      :ParameterRangeBegin begin
                                      :ParameterRangeEnd end
                                      :Value value}])
             [:AdditionalAttributes 0] errors)

            "FLOAT" "3.0" "1.0" "2.0"
            ["Parameter Range Begin [3.0] cannot be greater than Parameter Range End [1.0]."]

            "INT" "3" "1" "2"
            ["Parameter Range Begin [3] cannot be greater than Parameter Range End [1]."]

            "DATE" "1986-10-16" "1986-10-14" "1986-10-15"
            ["Parameter Range Begin [1986-10-16] cannot be greater than Parameter Range End [1986-10-14]."]

            "TIME" "04:03:29Z" "04:03:27Z" "04:03:28Z"
            ["Parameter Range Begin [04:03:29Z] cannot be greater than Parameter Range End [04:03:27Z]."]

            "DATETIME" "1986-10-14T04:03:29.0Z" "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:28Z"
            ["Parameter Range Begin [1986-10-14T04:03:29.0Z] cannot be greater than Parameter Range End [1986-10-14T04:03:27.0Z]."]))
        (testing "value is less than parameter range begin"
          (are [data-type begin end value errors]
            (h/assert-invalid
             (coll-with-add-attribs [{:Name "foo"
                                      :DataType data-type
                                      :ParameterRangeBegin begin
                                      :ParameterRangeEnd end
                                      :Value value}])
             [:AdditionalAttributes 0] errors)

            "FLOAT" "2.0" "3.0" "1.0"
            ["Value [1.0] cannot be less than Parameter Range Begin [2.0]."]

            "INT" "2" "3" "1"
            ["Value [1] cannot be less than Parameter Range Begin [2]."]

            "DATE" "1986-10-15" "1986-10-16" "1986-10-14"
            ["Value [1986-10-14] cannot be less than Parameter Range Begin [1986-10-15]."]

            "TIME" "04:03:28Z" "04:03:29Z" "04:03:27Z"
            ["Value [04:03:27Z] cannot be less than Parameter Range Begin [04:03:28Z]."]

            "DATETIME" "1986-10-14T04:03:28Z" "1986-10-14T04:03:29Z" "1986-10-14T04:03:27Z"
            ["Value [1986-10-14T04:03:27Z] cannot be less than Parameter Range Begin [1986-10-14T04:03:28Z]."]))
        (testing "value is greater than parameter range end"
          (are [data-type begin end value errors]
            (h/assert-invalid
             (coll-with-add-attribs [{:Name "foo"
                                      :DataType data-type
                                      :ParameterRangeBegin begin
                                      :ParameterRangeEnd end
                                      :Value value}])
             [:AdditionalAttributes 0] errors)

            "FLOAT" "1.0" "2.0" "3.0"
            ["Value [3.0] cannot be greater than Parameter Range End [2.0]."]

            "INT" "1" "2" "3"
            ["Value [3] cannot be greater than Parameter Range End [2]."]

            "DATE" "1986-10-14" "1986-10-15" "1986-10-16"
            ["Value [1986-10-16] cannot be greater than Parameter Range End [1986-10-15]."]

            "TIME" "04:03:27Z" "04:03:28Z" "04:03:29Z"
            ["Value [04:03:29Z] cannot be greater than Parameter Range End [04:03:28Z]."]

            "DATETIME" "1986-10-14T04:03:27Z" "1986-10-14T04:03:28Z" "1986-10-14T04:03:29Z"
            ["Value [1986-10-14T04:03:29Z] cannot be greater than Parameter Range End [1986-10-14T04:03:28Z]."]))))))
