(ns cmr.common-app.test.services.ingest.subscription
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]))

(deftest normalize-parameters-test
  "Query normalization, should be sorted parameters"
  (testing "With a leading question mark"
   (let [query "provider=PROV1&instrument=1B&instrument=2B"
         expected "instrument=1B&instrument=2B&provider=PROV1"
         actual (sub-common/normalize-parameters query)]
     (is (= expected actual))))
  (testing "Without a leading question mark"
   (let [query "provider=PROV1&instrument=1B&instrument=2B"
         expected "instrument=1B&instrument=2B&provider=PROV1"
         actual (sub-common/normalize-parameters query)]
     (is (= expected actual))))
  (testing "Empty string"
   (let [query ""
         expected ""
         actual (sub-common/normalize-parameters query)]
     (is (= expected actual)))))
