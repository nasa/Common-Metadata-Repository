(ns cmr.umm-spec.test.iso-keywords
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.iso-keywords :as iso-keywords]))

(deftest science-keyword-empty-value-test
  (testing "Empty Topic is rejected when `sanitize?` is false"
    (let [cmr-6840-example-collection (-> "example-data/special-case-files/CMR-6840-empty-facet-titles.xml"
                                          io/resource
                                          io/file
                                          slurp)]
      ;; Parse function should return nil - it is throwing a service error that
      ;; that we do not catch in this unit test
      (is (= nil (iso-keywords/parse-science-keywords cmr-6840-example-collection false))))))
