(ns cmr.exchange.geo.tests.unit.geom.util
  (:require
   [clojure.test :refer :all]
   [cmr.exchange.geo.geom.util :as util]))

(deftest earth-radius-squared
  (is (= 4.0680631590769E13
         util/earth-radius**2)))

(deftest earth-eccentricity-squared
  (is (= 0.0066943799901414
         util/earth-eccentricity**2)))

(deftest ll->cartesian
  (is (= [-8555172.57232537 4013180.9897545036]
         (util/ll->cartesian 38.9917856 -76.8524228)))
  (is (= [4743253.928019642 -1670935.6471926358]
         (util/ll->cartesian -15.1875 42.609375))))

(deftest lla->ecef
  (is (= [1129088.953571938 -4833784.549103725 3991608.2828432056]
         (util/lla->ecef 38.9917856 -76.8524228 0)))
  (is (= [4531310.7649441995 4168122.4168595946 -1660131.1898801303]
         (util/lla->ecef -15.1875 42.609375 0))))

(deftest ecef->lla
  (is (= [38.991785600157165 283.1475772 1.4124438166618347E-5]
         (util/ecef->lla 1129088.953571938 -4833784.549103725 3991608.2828432056))))
