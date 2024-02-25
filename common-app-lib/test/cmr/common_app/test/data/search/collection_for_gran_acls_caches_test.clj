(ns cmr.common-app.test.data.search.collection-for-gran-acls-caches-test
 (:require
  [clojure.test :refer :all]
  [clojure.test.check.generators :as gen]
  [cmr.common-app.data.search.collection-for-gran-acls-caches :as cmn-coll-for-gran-acls-caches]))

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
                   cmn-coll-for-gran-acls-caches/time-strs->clj-times
                   cmn-coll-for-gran-acls-caches/clj-times->time-strs)]
   (is (= some-text (:a-field actual)) "field should not change")
   (is (= expected-date (str (:point-of-time actual))) "Date should exist"))))
