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
      (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType :string}
                                                        {:Name "bar" :DataType :string}])))
    (testing "invalid additional attributes names"
      (testing "duplicate names"
        (let [coll (coll-with-additional-attributes [{:Name "foo" :DataType :string}
                                                     {:Name "foo" :DataType :string}
                                                     {:Name "bar" :DataType :string}
                                                     {:Name "bar" :DataType :string}
                                                     {:Name "charlie" :DataType :string}])]
          (h/assert-invalid
            coll
            [:AdditionalAttributes]
            ["Additional Attributes must be unique. This contains duplicates named [foo, bar]."])))))

  (testing "additional attributes data type"
    (testing "valid data types"
      (are [data-type]
           (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type}]))
           :string
           :float
           :int
           :boolean
           :date
           :time
           :datetime
           :date-string
           :time-string
           :datetime-string)))

  (testing "additional attributes values match data type"
    (testing "valid values"
      (are [data-type value]
           (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type :Value value}]))
           :string "string value"
           :float "1.0"
           :int "1"
           :boolean "true"
           :date "1986-10-14"
           :time "04:03:27.123Z"
           :datetime "1986-10-14T04:03:27.0Z"
           :date-string "1986-10-14"
           :time-string "04:03:27.123"
           :datetime-string "1986-10-14T04:03:27.0Z"
           :string nil
           :float nil
           :int nil
           :boolean nil
           :date nil
           :time nil
           :datetime nil
           :date-string nil
           :time-string nil
           :datetime-string nil)
      (are [data-type value]
           (and
             (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type :ParamteterRangeBegin value}]))
             (h/assert-valid (coll-with-additional-attributes [{:Name "foo" :DataType data-type :ParameterRangeEnd value}])))
           :float "1.0"
           :int "1"
           :date "1986-10-14"
           :time "04:03:27.123Z"
           :datetime "1986-10-14T04:03:27.0Z"
           :date-string "1986-10-14"
           :time-string "04:03:27.123"
           :datetime-string "1986-10-14T04:03:27.0Z"
           :string nil
           :float nil
           :int nil
           :boolean nil
           :date nil
           :time nil
           :datetime nil
           :date-string nil
           :time-string nil
           :datetime-string nil))

    (testing "invalid values"
      (are [data-type value field errors]
           (h/assert-invalid
             (coll-with-additional-attributes [{:Name "foo" :DataType data-type field value}])
             [:AdditionalAttributes 0] errors)

           :boolean "true" :ParameterRangeBegin ["Parameter Range Begin is not allowed for type [BOOLEAN]"]
           :float "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [FLOAT]."]
           :int "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [INT]."]
           :date "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [DATE]."]
           :time "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [TIME]."]
           :datetime "bar" :ParameterRangeBegin ["Parameter Range Begin [bar] is not a valid value for type [DATETIME]."]

           :boolean "true" :ParameterRangeEnd ["Parameter Range End is not allowed for type [BOOLEAN]"]
           :float "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [FLOAT]."]
           :int "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [INT]."]
           :date "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [DATE]."]
           :time "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [TIME]."]
           :datetime "bar" :ParameterRangeEnd ["Parameter Range End [bar] is not a valid value for type [DATETIME]."]

           :boolean "bar" :Value ["Value [bar] is not a valid value for type [BOOLEAN]."]
           :float "bar" :Value ["Value [bar] is not a valid value for type [FLOAT]."]
           :int "bar" :Value ["Value [bar] is not a valid value for type [INT]."]
           :date "bar" :Value ["Value [bar] is not a valid value for type [DATE]."]
           :time "bar" :Value ["Value [bar] is not a valid value for type [TIME]."]
           :datetime "bar" :Value ["Value [bar] is not a valid value for type [DATETIME]."]))

    (testing "multiple invalid values"
      (h/assert-multiple-invalid
        (coll-with-additional-attributes [{:Name "foo" :DataType :float :Value "str"}
                                          {:Name "bar" :DataType :float :Value "1.0"}
                                          {:Name "baz" :DataType :int :Value "1.0"}])
        [{:path [:AdditionalAttributes 0]
          :errors
          ["Value [str] is not a valid value for type [FLOAT]."]}
         {:path [:AdditionalAttributes 2]
          :errors
          ["Value [1.0] is not a valid value for type [INT]."]}]))))
