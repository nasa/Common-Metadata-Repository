(ns cmr.search.test.results-handlers.timeline-results-handler
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [cmr.search.results-handlers.timeline-results-handler :as trh]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(deftest adjacent-interval-test
  (testing "year"
    (are [y1 y2 adjacent?]
         (let [t1 (t/date-time y1)
               t2 (t/date-time y2)]
           (= adjacent?
              (trh/adjacent? :year {:end t1} {:start t2})))
         1999 2000 true
         2001 2002 true
         2001 2003 false))
  (testing "month"
    (are [y1 m1 y2 m2 adjacent?]
         (let [t1 (t/date-time y1 m1)
               t2 (t/date-time y2 m2)]
           (= adjacent?
              (trh/adjacent? :month {:end t1} {:start t2})))
         1999 12 2000 1 true
         2001 12 2002 1 true
         2000 1 2000 2 true
         2000 1 2001 2 false
         2000 4 2000 6 false
         2000 1 2003 2 false))
  (testing "day"
    (are [y1 m1 d1 y2 m2 d2 adjacent?]
         (let [t1 (t/date-time y1 m1 d1)
               t2 (t/date-time y2 m2 d2)]
           (= adjacent?
              (trh/adjacent? :day {:end t1} {:start t2})))
         1999 12 31 2000 1 1 true
         2001 12 31 2002 1 1 true
         2000 1 1 2000 1 2 true
         2000 1 31 2001 2 1 false
         2000 4 5 2000 4 7 false
         2000 1 1 2003 1 2 false))
  (testing "hour"
    (are [targs1 targs2 adjacent?]
         (let [t1 (apply t/date-time targs1)
               t2 (apply t/date-time targs2)]
           (= adjacent?
              (trh/adjacent? :hour {:end t1} {:start t2})))
         [1999 12 31 23] [2000 1 1 0] true
         [2001 12 31 23] [2002 1 1 0] true
         [2000 1 1 23] [2000 1 2 0] true
         [2000 1 31 23] [2001 2 1 0] false
         [2000 4 5 4] [2000 4 5 5] true
         [2000 4 5 4] [2000 4 5 6] false)))







