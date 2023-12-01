(ns cmr.search.test.unit.services.acls.collections-cache
  (:require
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.search.services.acls.collections-cache :as coll-cache]))

(defn- random-text
  "Create a random string by combining all the values from gen/string-alphanumeric"
  []
  (apply str (vec (gen/sample gen/string-alphanumeric))))

(deftest make-dates-safe-for-serialize-test
  "Confirm that an object can be serialized to text and then back"
  (testing "round trip"
    (let [some-text (random-text)
          some-date "2024-12-31T4:3:2"
          supplied-data {:point-of-time some-date :a-field some-text}
          expected-date "2024-12-31T04:03:02.000Z"
          actual (-> supplied-data
                     coll-cache/time-strs->clj-times
                     coll-cache/clj-times->time-strs)]
      (is (= some-text (:a-field actual)) "field should not change")
      (is (= expected-date (str (:point-of-time actual))) "Date should exist"))))
