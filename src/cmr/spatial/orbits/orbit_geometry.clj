(ns cmr.spatial.orbits.orbit-geometry
  "Contains functions for converting orbit parameters into polygons"
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.services.errors :as errors]
            [primitive-math]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.orbits.echo-orbits :as echo-orbits]
            [cmr.spatial.orbits.coordinate :as coordinate]))

(primitive-math/use-primitive-operators)

;; Initial implementation here is a complete straightforward reimplementation of ruby to clojure

;; TODO rename and refactor as appropriate.
;; - some of this code should be moved into the indexer



(defn safe-asin
  ^double [^double v]
  (asin (if (>= v 0.0)
          (min v 1.0)
          (max v -1.0))))

(defn safe-acos
  ^double [^double v]
  (acos (if (>= v 0.0)
          (min v 1.0)
          (max v -1.0))))

(defn longitude-correction
  ^double [^double time-elapsed-secs]
  (mod (* (/ TAU echo-orbits/SOLAR_DAY_S) time-elapsed-secs) TAU))

(defn ground-track-uncorrected
  [orbit-parameters ^double ascending-crossing-lon-rad ^double alpha]
  (let [inclination-rad (radians (:inclination-angle orbit-parameters))
        ground-track-lat (asin (* (sin inclination-rad) (sin alpha)))
        ground-track-lon (if (= (abs ground-track-lat) (/ PI 2.0))
                           ascending-crossing-lon-rad
                           (let [drift (safe-acos (/ (cos alpha) (cos ground-track-lat)))]
                             (- ascending-crossing-lon-rad
                                (* (if (<= alpha PI)
                                     drift
                                     (- TAU drift))
                                   (if (echo-orbits/retrograde? orbit-parameters)
                                     1.0
                                     -1.0)))))]
    (coordinate/from-phi-theta ground-track-lat ground-track-lon)))


(def ^:const ^double ALPHA_CORRECTION_DELTA
  "A small number to offset alpha to keep it from being exactly 0 or PI"
  0.00000001)

(defn alpha-negate
  "Multiplies v * -1 if alpha is greater than pi"
  [^double alpha ^double v]
  (if (> alpha PI)
    (* v -1.0)
    v))

(def ^:const ^double HALF_PI
  (/ PI 2.0))

;; TODO rename this
(defn along-track-swath-edges
  "TODO"
  [orbit-parameters ^double ascending-crossing-lon ^double time-elapsed-mins]
  (let [inclination-rad (echo-orbits/inclination-rad orbit-parameters)
        time-elapsed-secs (* time-elapsed-mins 60.0)
        ascending-crossing-lon-rad (radians ascending-crossing-lon)

        ^double alpha (mod (* time-elapsed-secs (echo-orbits/angular-velocity-rad-s orbit-parameters)) TAU)
        ^double alpha (if (or (= 0.0 alpha) (= PI alpha))
                        (+ alpha ALPHA_CORRECTION_DELTA)
                        alpha)

        r (/ (echo-orbits/swath-width-rad orbit-parameters) 2.0)
        coord (ground-track-uncorrected orbit-parameters 0 alpha)
        beta (acos (* (cos r) (cos (:phi coord)) (cos (:theta coord))))
        rR (safe-asin (/ (sin r) (sin beta)))

        lat-left (alpha-negate alpha (asin (* (sin (+ rR inclination-rad)) (sin beta))))
        rw (safe-acos (* (cos r) (cos (:phi coord)) (/ (cos (:theta coord)) (cos lat-left))))
        lon-left (- ascending-crossing-lon-rad
                    (* (if (<= alpha PI) rw (- TAU rw))
                       (if (< (+ rR inclination-rad) HALF_PI) -1.0 1.0)))

        lat-right (alpha-negate alpha (asin (* (sin (+ (* -1.0 rR) inclination-rad)) (sin beta))))
        re (safe-acos (* (cos r) (cos (:phi coord)) (/ (cos (:theta coord)) (cos lat-right))))
        lon-right (- ascending-crossing-lon-rad (* (if (<= alpha PI) re (- TAU re))
                                                   (if (< (- inclination-rad rR) HALF_PI) -1.0 1.0)))

        lon-correct (longitude-correction time-elapsed-secs)
        edge [(coordinate/from-phi-theta lat-left (- lon-left lon-correct))
              (coordinate/from-phi-theta lat-right (- lon-right lon-correct))]]
    (if (< alpha PI)
      edge
      (reverse edge))))

(comment

  ;; an example from orbit geometry spec
  (def test-orbit-parameters
    {:inclination-angle 120.0
     :period 100.0
     :swath-width 1450.0
     :start-circular-latitude -90.0
     :number-of-orbits 0.5})

  (for [{:keys [lon lat]} (along-track-swath-edges test-orbit-parameters 50.0 10)]
    [lon lat])

  (ground-track
    test-orbit-parameters
    0.0
    30.0)


  )

(defn ground-track
  "TODO"
  [orbit-parameters ^double ascending-crossing-lon ^double time-elapsed-mins]
  (let [time-elapsed-secs (* 60.0 time-elapsed-mins)
        ascending-crossing-lon-rad (radians ascending-crossing-lon)
        alpha (mod (* time-elapsed-secs (echo-orbits/angular-velocity-rad-s orbit-parameters)) TAU)
        coord (ground-track-uncorrected orbit-parameters ascending-crossing-lon-rad alpha)]
    (coordinate/from-phi-theta (:phi coord) (- ^double (:theta coord)
                                               (longitude-correction time-elapsed-secs)))))

