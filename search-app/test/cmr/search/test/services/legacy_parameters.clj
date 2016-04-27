(ns cmr.search.test.services.legacy-parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.messages.attribute-messages :as a-msg]
            [cmr.common.test.test-util :as tu])
  (:import clojure.lang.ExceptionInfo))

;; Test for mixed parameters
(deftest mixed-paramter-types
  (testing "attribute[][name] style"
    (tu/assert-exception-thrown-with-errors
      :bad-request
      [(a-msg/mixed-legacy-and-cmr-style-parameters-msg)]
      (lp/process-legacy-psa {:attribute ["string,abc,xyz"
                                          {:name "PDQ"}
                                          {:type "string"}
                                          {:value "ABC"}]})))

  (testing "attribute[0][name] style"
    (tu/assert-exception-thrown-with-errors
      :bad-request
      [(a-msg/mixed-legacy-and-cmr-style-parameters-msg)]
      (lp/process-legacy-psa {:attribute ["string,abc,xyz"
                                          {0 {:name "PDQ"
                                              :type "string"
                                              :value "ABC"}}]}))))

(deftest multiple-attributes
  (testing "attribute[][name] style only allows one PSA."
    (tu/assert-exception-thrown-with-errors
      :bad-request
      ["Duplicate parameters are not allowed [name = XYZ]."]
      (lp/process-legacy-psa {:attribute [{:name "PDQ"}
                                          {:type "string"}
                                          {:value "ABC"}
                                          {:name "XYZ"}]})))

  (testing "attribute[0][name] style allows multiple PSAs."
    (lp/process-legacy-psa {:attribute {0 {:name "PDQ"
                                           :type "string"
                                           :value "ABC"}
                                        1 {:name "XYZ"
                                           :type "string"
                                           :value "ABC"}}})))

