(ns cmr.umm.test.collection.product-specific-attribute
  "Tests parsing product specific attribute components"
  (:require [clojure.test :refer :all]
            [cmr.umm.collection.product-specific-attribute :as psa]
            [cmr.umm.umm-collection :as umm-c]
            [clj-time.core :as t]))


(deftest parse-value-test
  (testing "parse int"
    (are [value string] (= value (psa/parse-value :int string))
         1 "1"
         123 "123"
         -12 "-12"))
  (testing "parse float"
    (are [value string] (= value (psa/parse-value :float string))
         1.0 "1"
         1.0 "1.0"
         1123456.0 "1123456"
         -12.0 "-12"
         -12.5 "-12.50"))
  (testing "parse boolean"
    (are [value string] (= value (psa/parse-value :boolean string))
         true "true"
         false "false"
         true "1"
         false "0"))
  (testing "parse datetime"
    (are [millis string] (= (t/date-time 1986 10 14 4 3 27 millis)
                            (psa/parse-value :datetime string))
         456 "1986-10-14T04:03:27.456Z"
         0 "1986-10-14T04:03:27.0Z"
         0 "1986-10-14T04:03:27Z"
         0 "1986-10-14T04:03:27"
         456 "1986-10-14T04:03:27.456-00:00"
         456 "1986-10-14T03:03:27.456-01:00"))
  (testing "parse time"
    (are [millis string] (= (t/date-time 1970 1 1 4 3 27 millis)
                            (psa/parse-value :time string))
         456 "04:03:27.456"
         0 "04:03:27.0"
         0 "04:03:27"
         0 "04:03:27Z"
         123 "04:03:27.123Z"
         0 "03:03:27-01:00"
         123 "03:03:27.123-01:00"))
  (testing "parse date"
    (are [string] (= (t/date-time 1986 10 14)
                     (psa/parse-value :date string))
         "1986-10-14"
         "1986-10-14Z"))
  (testing "parse nils"
    (doseq [type umm-c/product-specific-attribute-types]
      (is (nil? (psa/parse-value type nil))))))

(deftest gen-value-test
  (testing "gen nils"
    (doseq [type umm-c/product-specific-attribute-types]
      (is (nil? (psa/gen-value type nil))))))
