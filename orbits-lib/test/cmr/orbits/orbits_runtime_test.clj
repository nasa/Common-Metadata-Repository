(ns cmr.orbits.orbits-runtime-test
  "This tests that the basics work with the orbits runtime. More thorough tests for this are in the
   Ruby Specs test."
  (:require
   [clojure.test :refer :all]
   [cmr.common.lifecycle :as l]
   [cmr.orbits.orbits-runtime :as o]))

(deftest test-area-crossing-range
  (let [orbits-runtime (l/start (o/create-orbits-runtime) nil)]
    (is (= [[[[-45 45]] [[-65.92018254333931 65.92018254333937]]]]
           (o/area-crossing-range
            orbits-runtime
            {:lat-range [-45 45]
             :geometry-type :br
             :coords [-45, 45, 45, -45]
             :ascending? true
             :inclination 98.15
             :period 98.88
             :swath-width 1450.0
             :start-clat -90.0
             :num-orbits 0.5})))))

(deftest test-denormalize-latitude-range
  (let [orbits-runtime (l/start (o/create-orbits-runtime) nil)]
    (is (= [[[50 360] [410 720]] [[-180 130] [180 490] [540 850]]]
           (o/denormalize-latitude-range orbits-runtime 50 720)))))
