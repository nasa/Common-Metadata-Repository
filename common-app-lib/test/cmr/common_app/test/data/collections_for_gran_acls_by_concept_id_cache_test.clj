(ns cmr.common-app.test.data.collections-for-gran-acls-by-concept-id-cache-test
 (:require
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common-app.data.collections-for-gran-acls-by-concept-id-cache :as coll-for-gran-acls-cache]))

(defn- random-text
 "Create a random string by combining all the values from gen/string-alphanumeric"
 []
 (apply str (vec (gen/sample gen/string-alphanumeric))))

(def create-context
  "Creates a testing concept with the KMS caches."
  {:system {:caches {coll-for-gran-acls-cache/coll-by-concept-id-cache-key (coll-for-gran-acls-cache/create-coll-by-concept-id-cache-client)}}})

(deftest make-dates-safe-for-serialize-test
 "Confirm that an object can be serialized to text and then back"
 (testing "round trip"
  (let [some-text (random-text)
        some-date "2024-12-31T4:3:2"
        supplied-data {:point-of-time some-date :a-field some-text}
        expected-date "2024-12-31T04:03:02.000Z"
        actual (-> supplied-data
                   coll-for-gran-acls-cache/time-strs->clj-times
                   coll-for-gran-acls-cache/clj-times->time-strs)]
   (is (= some-text (:a-field actual)) "field should not change")
   (is (= expected-date (str (:point-of-time actual))) "Date should exist"))))

(deftest get-collection-for-gran-acls-test
  (testing "redis connection error"
    (is (thrown? Exception (coll-for-gran-acls-cache/get-collection-for-gran-acls create-context "C1234")))))
