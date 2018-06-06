(ns cmr.opendap.tests.unit.ous.util.geog
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.const :as const]
    [cmr.opendap.ous.util.geog :as geog]
    [ring.util.codec :as codec]))

(deftest lon-lo-phase-shift
  (is (= 0 (geog/lon-lo-phase-shift 360 -180)))
  (is (= 179 (geog/lon-lo-phase-shift 360 0)))
  (is (= 359 (geog/lon-lo-phase-shift 360 180)))
  (is (= 156 (geog/lon-lo-phase-shift 360 -23.0625))))

(deftest lon-hi-phase-shift
  (is (= 0 (geog/lon-hi-phase-shift 360 -180)))
  (is (= 180 (geog/lon-hi-phase-shift 360 0)))
  (is (= 359 (geog/lon-hi-phase-shift 360 180)))
  (is (= 237 (geog/lon-hi-phase-shift 360 57.09375))))

(deftest lat-lo-phase-shift
  (is (= 179 (geog/lat-lo-phase-shift 180 -90)))
  (is (= 90 (geog/lat-lo-phase-shift 180 0)))
  (is (= 0 (geog/lat-lo-phase-shift 180 90)))
  (is (= 27 (geog/lat-lo-phase-shift 180 63.5625))))

(deftest lat-hi-phase-shift
  (is (= 179 (geog/lat-hi-phase-shift 180 -90)))
  (is (= 89 (geog/lat-hi-phase-shift 180 0)))
  (is (= 0 (geog/lat-hi-phase-shift 180 90)))
  (is (= 23 (geog/lat-hi-phase-shift 180 66.09375))))
