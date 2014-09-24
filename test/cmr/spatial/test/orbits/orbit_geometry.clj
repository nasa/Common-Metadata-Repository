(ns cmr.spatial.test.orbits.orbit-geometry
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.orbits.orbits :refer :all]
            [cmr.spatial.orbits.swath-geometry :refer :all]))

(defn close-to-swath?
  [[p0 p1] [[lon0 lat0] [lon1 lat1]]]
  (and (approx= (:lat p0) lat0)
       (approx= (:lon p0) lon0)
       (approx= (:lat p1) lat1)
       (approx= (:lon p1) lon1)))

; For testing a couple of key private methods
(def ^:private temporal-offset-range #'cmr.spatial.orbits.swath-geometry/temporal-offset-range)
(def ^:private ascending-crossing-time #'cmr.spatial.orbits.swath-geometry/ascending-crossing-time)

(def ^:private default-orbit-parameters {:inclination-angle 98.2
                                     :period 100.0
                                     :swath-width 2600.0
                                     :start-circular-latitude 50.0
                                     :number-of-orbits 2.0})

(def ^:private default-granule-orbit {:ascending-crossing 88.0
                                      :start-lat 0.0
                                      :start-direction "A"
                                      :end-lat 0.0
                                      :end-direction "A"})

(def ^:private default-calculated-spatial-domain {:orbit-number 10
                                                  :equator-crossing-longitude 88.0
                                                  :equator-crossing-date-time (t/date-time 2000  1  1  0  0  0)})

(def ^:private default-temporal {:range-date-time {:beginning-date-time (t/date-time 2000  1  1  0  0  0)
                                                      :ending-date-time (t/date-time 2000  1  1  3 20  0)}}) ; 2 orbit periods later


(def ^:private make-orbit-parameters (partial merge default-orbit-parameters))
(def ^:private make-granule-orbit (partial merge default-granule-orbit))
(def ^:private make-calculated-spatial-domain (partial merge default-calculated-spatial-domain))
(def ^:private make-temporal (partial merge default-temporal))

(deftest temporal-offset-range-test
  (testing "returns temporal offset ranges in seconds from the passed crossing time"
    (let [temporal {:range-date-time {:beginning-date-time (t/date-time 2000  1  1  0  0  0)
                                         :ending-date-time (t/date-time 2000  1  1  0  1  0)}}
          crossing-time (t/date-time 2000  1  1  0  0 20)]
      (is (= [-20 40] (temporal-offset-range temporal crossing-time))))))

(deftest ascending-crossing-time-test
  (let [orbit-params (make-orbit-parameters {:period 100.0})
        granule-orbit (make-granule-orbit {:ascending-crossing 88.0})]
    (testing "when the orbit calculated spatial domains have an entry for the crossing longitude"
      (let [ocsds [(make-calculated-spatial-domain {:equator-crossing-longitude 88.0 :equator-crossing-date-time (t/date-time 2000  1  1  0 50  0)})
                   (make-calculated-spatial-domain {:equator-crossing-longitude 90.0 :equator-crossing-date-time (t/date-time 2000  1  1  2 30  0)})]]
        (testing "returns the entry's date time"
          (is (= (t/date-time 2000  1  1  0 50  0) (ascending-crossing-time orbit-params granule-orbit ocsds))))))

    (testing "when the orbit calculated spatial domains have no entry for the crossing longitude"
      (let [ocsds [(make-calculated-spatial-domain {:equator-crossing-longitude 90.0 :equator-crossing-date-time (t/date-time 2000  1  1  0 50  0)})
                   (make-calculated-spatial-domain {:equator-crossing-longitude 92.0 :equator-crossing-date-time (t/date-time 2000  1  1  2 30  0)})]]
        (testing "a time half a period before the first calculated spatial domain entry"
          (is (= (t/date-time 2000  1  1  0  0  0) (ascending-crossing-time orbit-params granule-orbit ocsds))))))))

(deftest to-swath-test
  (testing "returns entries consisting of two points representing left and right swath edges"
    (let [orbit-params (make-orbit-parameters)
          swath (to-swath orbit-params (make-granule-orbit) [(make-calculated-spatial-domain)] (make-temporal))
          [left-edge right-edge] (first swath)]
      (is (not (= left-edge right-edge))) ; not the same point
      (is (= 0.0 (/ (+ (:lat left-edge) (:lat right-edge)) 2.0))) ; centered on the equator
      (is (= 88.0 (/ (+ (:lon left-edge) (:lon right-edge)) 2.0))) ; centered on 88 lat
      (is (> 0 (:lat left-edge))) ; left edge is below the equator
      (is (< 0 (:lat right-edge))) ; right edge is above the equator
      (is (approx= (swath-width-rad orbit-params) (p/angular-distance left-edge right-edge))) ; swath-width apart
    ))
  (testing "returns an entry for each time interval"
    (let [swath (to-swath (make-orbit-parameters) (make-granule-orbit) [(make-calculated-spatial-domain)] (make-temporal))]
      (is (= 201 (count swath))) ; temporal extent is 200 minutes + 1 for the inclusive last minute
    ))
  (testing "returns an entry for each interval when time intervals do not evenly divide the orbit"
    (let [swath (to-swath (make-orbit-parameters) (make-granule-orbit) [(make-calculated-spatial-domain)] (make-temporal) (* 60 150))]
      (is (= 3 (count swath))) ; Entries at t=0, t=150, t=200
    ))
  (testing "returns entries along the orbit"
    ; TODO: If the backtracking algorithm ever becomes available to this project, backtracking each point on a variety of orbits
    ;       would serve as an additional check
    (let [swath (to-swath (make-orbit-parameters) (make-granule-orbit) [(make-calculated-spatial-domain)] (make-temporal) 600)]
      (is (close-to-swath? (nth swath 0) [[76.41862128042021 -1.6570985493644115] [99.58137871957977 1.6570985493644115]]))
      (is (close-to-swath? (nth swath 1) [[70.85670428936116 32.73641825556157] [99.01304126948644 36.77045950909374]]))
      (is (close-to-swath? (nth swath 2) [[45.21826435122187 63.23545546556328] [105.63711155205173 71.93405939073072]]))
      (is (close-to-swath? (nth swath 3) [[-36.718264351221855 63.235455465563284] [-97.13711155205176 71.93405939073075]]))
    ))
)
