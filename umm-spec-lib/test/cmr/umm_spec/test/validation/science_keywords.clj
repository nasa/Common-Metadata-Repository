(ns cmr.umm-spec.test.validation.science-keywords
  "This has tests for UMM-C Science Keywords validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.validation.umm-spec-validation-core :as v]
            [cmr.umm-spec.models.umm-common-models :as c]
            [cmr.umm-spec.models.umm-collection-models :as coll]
            [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]
            [cmr.common.services.errors :as e]))

(deftest collection-science-keywords-validation
  (testing "valid collection science keywords"
    (h/assert-valid (coll/map->UMM-C
                     {:ScienceKeywords
                      [(c/map->ScienceKeywordType {:Category "c1"
                                                   :Topic "t1"
                                                   :Term "term1"
                                                   :VariableLevel1 "v1"
                                                   :VariableLevel2 "v2"
                                                   :VariableLevel3 "v3"
                                                   :DetailedVariable "dv"})
                       (c/map->ScienceKeywordType {:Category "c2"
                                                   :Topic "t2"
                                                   :Term "term2"})]})))

  (testing "invalid collection science keywords"
    (testing "missing category"
      (let [coll (coll/map->UMM-C
                   {:ScienceKeywords
                    [(c/map->ScienceKeywordType {:Topic "t1"
                                                 :Term "term1"})]})]
        (h/assert-invalid
          coll
          [:ScienceKeywords 0 :Category]
          ["Category is required."])))
    (testing "missing topic"
      (let [coll (coll/map->UMM-C
                   {:ScienceKeywords
                    [(c/map->ScienceKeywordType {:Category "c1"
                                                 :Term "term1"})]})]
        (h/assert-invalid
          coll
          [:ScienceKeywords 0 :Topic]
          ["Topic is required."])))
    (testing "missing terms"
      (let [coll (coll/map->UMM-C
                   {:ScienceKeywords
                    [(c/map->ScienceKeywordType {:Category "c1"
                                                 :Topic "t1"})]})]
        (h/assert-invalid
          coll
          [:ScienceKeywords 0 :Term]
          ["Term is required."])))
    (testing "multiple errors"
      (let [coll (coll/map->UMM-C
                   {:ScienceKeywords
                    [(c/map->ScienceKeywordType {:Category "c1"
                                                 :Topic "t1"})
                     (c/map->ScienceKeywordType {:Category "c1"
                                                 :Term "term1"})]})]
        (h/assert-multiple-invalid
          coll
          [{:path [:ScienceKeywords 0 :Term]
            :errors
            ["Term is required."]}
           {:path [:ScienceKeywords 1 :Topic]
            :errors
            ["Topic is required."]}])))))
