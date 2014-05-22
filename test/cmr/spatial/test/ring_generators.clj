(ns cmr.spatial.test.ring-generators
  "Tests that sanity check the ring generators"
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.math.combinatorics :as combo]
            [clojure.string :as str]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.arc :as a]
            [cmr.spatial.ring :as r]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.derived :as d]
            [cmr.spatial.test.generators :as sgen]))

;; Ring tests. These are functions that return true if the ring is correct and false if invalid.

(defn start-and-end-with-same-point
  [{:keys [points]}]
  (= (first points) (last points)))

(defn points-unique
  "The points are all unique."
  [{:keys [points]}]
  (= (dec (count points)) (count (set points))))

(defn points-not-antipodal
  [{:keys [points]}]
  (let [unique-points (drop-last points)
        point-pairs (combo/combinations unique-points 2)]
    (nil? (some (partial apply p/antipodal?) point-pairs))))

(defn contains-1-or-0-poles
  [{:keys [contains-north-pole contains-south-pole]}]
  (not (and contains-north-pole contains-south-pole)))

(defn more-than-one-external-point
  [{:keys [external-points]}]
  (> (count external-points) 1))

(defn external-points-are-not-in-ring
  [{:keys [external-points] :as ring}]
  (nil? (some (partial r/covers-point? ring) external-points)))

(defn external-points-are-not-in-mbr
  [{:keys [external-points mbr]}]
  (nil? (some (partial mbr/covers-point? mbr) external-points)))

(defn mbr-contains-all-points
  [{:keys [points mbr]}]
  (every? (partial mbr/covers-point? mbr) points))

(defn no-self-intersections
  [ring]
  (empty? (r/self-intersections ring)))

(defmacro vars-map
  "Takes a list of vars and creates a map that uses the keyword version of the var name to the var value"
  [var-syms]
  (into {} (for [v var-syms]
             [(keyword v) @(resolve v)]) ))

(def ring-tests
  "A map of ring test names to functions that perform the test."
  (vars-map [start-and-end-with-same-point
             points-unique
             points-not-antipodal
             contains-1-or-0-poles
             more-than-one-external-point
             external-points-are-not-in-ring
             external-points-are-not-in-mbr
             mbr-contains-all-points
             no-self-intersections]))

(defn test-ring
  "Runs the ring through the ring tests. Returns a list of of the tests that failed for the ring"
  [ring]
  (reduce (fn [failures [test-fn-name test-fn]]
            (if (test-fn ring)
              failures
              (conj failures test-fn-name)))
          []
          ring-tests))


(defn print-failure
  [type ring]
  (println "--------------------------------------------------------------------")
  (try
    (println type "Ring failed" (test-ring ring))
    (catch Throwable e
      ;; ignore
      (println type "Ring failed with exception")))

  ;; Print out the ring in a way that it can be easily copied to the test.
  (println (pr-str (concat '(r/ring)
                           [(vec (map
                                   #(list 'p/point (:lon %) (:lat %))
                                   (:points ring)))])))

  (println (str "http://testbed.echo.nasa.gov/spatial-viz/ring_self_intersection?test_point_ordinates=2,2"
                "&ring_ordinates="
                (str/join "," (r/ring->ords ring)))))

;; Verifies that the three point rings have some fundamental things correct
(defspec rings-3-point-test {:times 1000 :printer-fn print-failure}
  (for-all [ring sgen/rings-3-point]
    (let [failed-tests (test-ring ring)]
      (empty? failed-tests))))

(defspec rings-generator-test {:times 1000 :printer-fn print-failure}
  (for-all [ring sgen/rings]
    (let [failed-tests (test-ring ring)]
      (empty? failed-tests))))

;; TODO we could add a new defspec or a variation which will attempt to print out the failing example using a protocol
;; which could be optionally implemented for a type.
;; May also add an option to defspec to print out some code to try it yourself.
;; - Would allow printing a ring with code. Also would allow printing a link to show ring in map.

;; Or modify it to take a list of named tests to run. Could call this defspecs

(comment
  ;; defspecs example

  (defspecs numbers-are-valid
    [n gen/int]
    (spec "numbers are positive"
          (> n 0))
    (spec "numbers are less than 100"
          (< n 100)))
)

