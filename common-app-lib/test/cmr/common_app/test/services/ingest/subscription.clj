(ns cmr.common-app.test.services.ingest.subscription
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]))

(deftest normalize-parameters-test
  "Query normalization, should be sorted parameters"
  (testing "With a leading question mark"
   (let [query "provider=PROV1&instrument=1B&instrument=2B"
         expected "bc71e563ac03a05d7c557608f868ce6a"
         actual (sub-common/normalize-parameters query)]
     (is (= expected actual))))
  (testing "Without a leading question mark"
   (let [query "provider=PROV1&instrument=1B&instrument=2B"
         expected "bc71e563ac03a05d7c557608f868ce6a"
         actual (sub-common/normalize-parameters query)]
     (is (= expected actual))))
  (testing "Empty string"
   (let [query ""
         expected "d41d8cd98f00b204e9800998ecf8427e"
         actual (sub-common/normalize-parameters query)]
     (is (= expected actual)))))
