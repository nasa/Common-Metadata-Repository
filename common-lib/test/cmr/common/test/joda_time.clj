(ns cmr.common.test.joda-time
  "Tests joda time."
  (:require
   [clojure.test :refer :all]
   [cmr.common.joda-time]
   [clj-time.core :as t]))

(deftest printing-and-parsing-joda-time
  (testing "print read time"
    (let [v (t/now)]
      (is (= v (read-string (pr-str v))))))
  (testing "print read time in a different time zone"
    (let [v (t/to-time-zone (t/now) (t/time-zone-for-offset -5))]
      (is (= v (read-string (pr-str v)))))))
