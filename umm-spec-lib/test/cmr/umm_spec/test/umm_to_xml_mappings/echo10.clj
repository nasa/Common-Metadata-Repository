(ns cmr.umm-spec.test.umm-to-xml-mappings.echo10
  "Tests to verify that echo10 records are generated correctly."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as common-util :refer [are3]]
    [cmr.umm-spec.umm-to-xml-mappings.echo10 :as echo10]))

(deftest echo10-find-first-available-distribution-price-test
  "Tests the find-first-available-distribution-price function."

  (let [actual-price-in-first-element {:ArchiveAndDistributionInformation
                                       {:FileDistributionInformation
                                         [{:FormatType "Native",
                                           :Fees "0",
                                           :Format "XLS, PDF, PNG"}
                                          {:FormatType "Native",
                                           :Format "HTML"}]}}
        actual-price-in-second-element {:ArchiveAndDistributionInformation
                                         {:FileDistributionInformation
                                           [{:FormatType "Native",
                                             :Format "XLS, PDF, PNG"}
                                            {:FormatType "Native",
                                             :Fees "0",
                                             :Format "HTML"}]}}
        actual-no-price {:ArchiveAndDistributionInformation
                          {:FileDistributionInformation
                            [{:FormatType "Native",
                              :Format "XLS, PDF, PNG"}
                             {:FormatType "Native",
                              :Format "HTML"}]}}]

    (are3 [expected-result actual-data]
      (is (= expected-result
             (echo10/find-first-available-distribution-price actual-data)))

      "Test when the price is in the first element."
      "0"
      actual-price-in-first-element

      "Tests when the price is in the second element."
      "0"
      actual-price-in-second-element

      "Tests when no price exists."
      nil
      actual-no-price)))
