(ns cmr.umm-spec.test.validation.umm-spec-variable-validation-tests
  "This has tests for UMM variable validations."
  (:require
   [clojure.test :refer [deftest is]]
   [cmr.umm-spec.models.umm-variable-models :as vm]))

(deftest umm-variable-creation
  (is (= [:AdditionalIdentifiers
          :DataType
          :Definition
          :Dimensions
          :FillValues
          :IndexRanges
          :InstanceInformation
          :LongName
          :MeasurementIdentifiers
          :MetadataSpecification
          :Name
          :Offset
          :RelatedURLs
          :SamplingIdentifiers
          :Scale
          :ScienceKeywords
          :Sets
          :StandardName
          :Units
          :ValidRanges
          :VariableSubType
          :VariableType]
         (-> {}
             (vm/map->UMM-Var)
             (keys)
             (sort)))))
