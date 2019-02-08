(ns cmr.spatial.test.ring-generators
  "Tests that sanity check the ring generators"
  (:require
   [clojure.math.combinatorics :as combo]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
   [cmr.spatial.arc :as a]
   [cmr.spatial.derived :as d]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as p]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.test.generators :as sgen]
   [cmr.spatial.validation :as v]))

;; Ring tests. These are functions that return true if the ring is correct and false if invalid.

(defn start-and-end-with-same-point
  [{:keys [points]}]
  (= (first points) (last points)))

(defn points-unique
  "The points are all unique."
  [{:keys [points]}]
  (= (dec (count points)) (count (set points))))

(defn consecutive-points-not-antipodal
  [{:keys [points]}]
  (let [point-pairs (partition 2 1 points)]
    (nil? (some (partial apply p/antipodal?) point-pairs))))

(defn contains-1-or-0-poles
  [{:keys [contains-north-pole contains-south-pole]}]
  (not (and contains-north-pole contains-south-pole)))

(defn more-than-one-external-point
  [{:keys [external-points]}]
  (> (count external-points) 1))

(defn external-points-are-not-in-ring
  [{:keys [external-points] :as ring}]
  (nil? (some (partial gr/covers-point? ring) external-points)))

(defn external-points-are-not-in-mbr
  [{:keys [external-points mbr]}]
  (nil? (some (partial mbr/geodetic-covers-point? mbr) external-points)))

(defn mbr-contains-all-points
  [{:keys [points mbr]}]
  (every? (partial mbr/geodetic-covers-point? mbr) points))

(defn no-self-intersections
  [ring]
  (empty? (rr/self-intersections ring)))

(defn not-inside-out
  [ring]
  (not (rr/inside-out? ring)))

(defmacro vars-map
  "Takes a list of vars and creates a map that uses the keyword version of the var name to the var value"
  [var-syms]
  (into {} (for [v var-syms]
             [(keyword v) @(resolve v)])))

(def ring-tests
  "A map of ring test names to functions that perform the test."
  {:geodetic (vars-map [start-and-end-with-same-point
                        points-unique
                        consecutive-points-not-antipodal
                        contains-1-or-0-poles
                        more-than-one-external-point
                        external-points-are-not-in-ring
                        external-points-are-not-in-mbr
                        mbr-contains-all-points
                        no-self-intersections])
   :cartesian (vars-map [start-and-end-with-same-point
                         points-unique
                         not-inside-out
                         mbr-contains-all-points
                         no-self-intersections])})


(defn test-ring
  "Runs the ring through the ring tests. Returns a list of of the tests that failed for the ring"
  [ring]
  (reduce (fn [failures [test-fn-name test-fn]]
            (if (test-fn ring)
              failures
              (conj failures test-fn-name)))
          []
          (ring-tests (rr/coordinate-system ring))))

(defn print-failure
  [type ring]
  (try
    (println type "Ring failed" (test-ring ring))
    (catch Throwable e
      ;; ignore
      (println type "Ring failed with exception")))
  (sgen/print-failed-ring type ring))


;; Verifies that the three point rings have some fundamental things correct
(defspec rings-3-point-test {:times 100 :printer-fn print-failure}
  (for-all [ring (gen/bind sgen/coordinate-system sgen/rings-3-point)]
    (let [failed-tests (test-ring ring)]
      (empty? failed-tests))))

(defspec geodetic-rings-generator-test {:times 100 :printer-fn print-failure}
  (for-all [ring (sgen/rings :geodetic)]
    (let [failed-tests (test-ring ring)]
      (and (empty? failed-tests)
           (empty? (v/validate ring))))))

(defspec cartesian-rings-generator-test {:times 100 :printer-fn print-failure}
  (for-all [ring (sgen/rings :cartesian)]
    (let [failed-tests (test-ring ring)]
      (empty? failed-tests))))

(defspec polygon-with-holes-generator-test {:times 50}
  (for-all [polygon sgen/polygons-with-holes]
    (let [rings (:rings polygon)
          failed-tests (mapcat test-ring rings)
          [boundary & holes] rings]
      (and (empty? failed-tests)
           (every? (partial rr/covers-ring? boundary) holes)))))
