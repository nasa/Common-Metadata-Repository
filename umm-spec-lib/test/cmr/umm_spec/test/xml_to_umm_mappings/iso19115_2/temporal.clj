(ns cmr.umm-spec.test.xml-to-umm-mappings.iso19115-2.temporal
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso]))

(deftest temporal-parsing-test
  (testing "Testing parsing the temporal extent")
  (let [value (-> (slurp (io/resource "example-data/iso19115/artificial_test_data.xml"))
                  (iso/parse-temporal-extents))
        value1 (-> (slurp (io/resource "example-data/iso19115/ISOExample-project.xml"))
                  (iso/parse-temporal-extents))]
    (is (= '("2003-07-15T23:59:59.999Z") 
           (->> (map :SingleDateTimes value)
               flatten 
               (remove nil?))))
    (is (= '("1971-11-20T00:00:00.000Z")
           (->> (map :SingleDateTimes value1)
                flatten
                (remove nil?))))))
