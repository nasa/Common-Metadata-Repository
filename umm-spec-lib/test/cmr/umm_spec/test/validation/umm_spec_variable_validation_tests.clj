(ns cmr.umm-spec.test.validation.umm-spec-variable-validation-tests
  "This has tests for UMM variable validations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as u :refer [are3]]
   [cmr.umm-spec.models.umm-common-models :as cm]
   [cmr.umm-spec.models.umm-variable-models :as vm]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as helpers]
   [cmr.umm-spec.validation.umm-spec-validation-core :as v]))

(deftest umm-variable-creation
  (is (= [:AdditionalIdentifiers
          :DataType
          :Definition
          :Dimensions
          :FillValues
          :IndexRanges
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
