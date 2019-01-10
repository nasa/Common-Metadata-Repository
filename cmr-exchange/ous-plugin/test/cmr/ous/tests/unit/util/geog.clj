(ns cmr.ous.tests.unit.util.geog
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [clojusc.twig :as logger]
    [cmr.ous.const :as const]
    [cmr.ous.util.geog :as geog]
    [ring.util.codec :as codec]))

(logger/set-level! '[] :fatal)

(deftest offset-index
  (is (= 359 (geog/offset-index 360 360 1)))
  (is (= 179 (geog/offset-index 180 180 1)))
  (let [result (geog/offset-index 179.0 180 1)]
    (is (float? result))
    (is (= 179.0 result)))
  (is (= 35999 (geog/offset-index 36000 36000 1)))
  (is (= 17999 (geog/offset-index 18000 18000 1)))
  (is (= 17999 (geog/offset-index 18000 17900 1)))
  (is (= 16000 (geog/offset-index 16000 17999 1))))

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
  (is (= 0 (geog/lat-lo-phase-shift 180 -90)))
  (is (= 89 (geog/lat-lo-phase-shift 180 0)))
  (is (= 179 (geog/lat-lo-phase-shift 180 90)))
  (is (= 152 (geog/lat-lo-phase-shift 180 63.5625))))

(deftest lat-hi-phase-shift
  (is (= 0 (geog/lat-hi-phase-shift 180 -90)))
  (is (= 90 (geog/lat-hi-phase-shift 180 0)))
  (is (= 179 (geog/lat-hi-phase-shift 180 90)))
  (is (= 156 (geog/lat-hi-phase-shift 180 66.09375))))

(deftest lat-lo-phase-shift-reversed
  (is (= 179 (geog/lat-lo-phase-shift-reversed 180 -90)))
  (is (= 90 (geog/lat-lo-phase-shift-reversed 180 0)))
  (is (= 0 (geog/lat-lo-phase-shift-reversed 180 90)))
  (is (= 27 (geog/lat-lo-phase-shift-reversed 180 63.5625))))

(deftest lat-hi-phase-shift-reversed
  (is (= 179 (geog/lat-hi-phase-shift-reversed 180 -90)))
  (is (= 89 (geog/lat-hi-phase-shift-reversed 180 0)))
  (is (= 0 (geog/lat-hi-phase-shift-reversed 180 90)))
  (is (= 23 (geog/lat-hi-phase-shift-reversed 180 66.09375))))
