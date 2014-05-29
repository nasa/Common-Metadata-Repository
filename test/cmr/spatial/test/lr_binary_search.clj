(ns cmr.spatial.test.lr-binary-search
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
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.lr-binary-search :as lbs]
            [cmr.spatial.dev.viz-helper :as viz-helper]))

(defspec all-rings-have-lrs {:times 1000 :printer-fn sgen/print-failed-ring}
  (for-all [ring sgen/rings]
    (let [lr (lbs/find-lr ring)]
      (and lr
           (r/covers-br? ring lr)))))

(comment
  ;; Visualization samples and helpers

  (def ring (d/calculate-derived
              (first (gen/sample sgen/rings 1))))

  ;; Samples
  ;; around north pole
  (display-draggable-lr-ring
    (r/ords->ring 45 85, 90 85, 135 85, 180 85, -135 85, -45 85, 45 85))

  ;; around south pole
  (display-draggable-lr-ring
    (r/ords->ring 45 -85, -45 -85, -135 -85, 180 -85, 135 -85, 90 -85, 45 -85))

    ;; across antimeridian
  (display-draggable-lr-ring
    (r/ords->ring 175 -10, -175 -10, -175 0, -175 10
                  175 10, 175 0, 175 -10))

  ;; Performance testing
  (require '[criterium.core :refer [with-progress-reporting bench]])

  (let [ring (d/calculate-derived (r/ords->ring 0,0, 4,0, 6,5, 2,5, 0,0))]
    (with-progress-reporting
      (bench
        (lbs/find-lr ring))))

  (defn br->ring-with-n-points
    "Creates a ring with at least n points from the br. Br must not cross antimeridian"
    [br ^double n]
    (let [{:keys [^double west ^double north ^double east ^double south]} br
          num-edge (/ n 2.0)
          edge-length (/ (- east west) num-edge)
          south-points (for [i (range num-edge)]
                         (p/point (+ west (* i edge-length)) south))
          north-points (for [i (range num-edge)]
                         (p/point (- east (* i edge-length)) north))]

      (r/ring (concat south-points north-points [(first south-points)]))))

  (let [ring (d/calculate-derived (br->ring-with-n-points (mbr/mbr -170 45 170 -45) 2000.0))]
    (with-progress-reporting
      (bench
        (lbs/find-lr ring))))

  ; 1 - 950ms
  ; 2 - 450 ms (changed ring to cache the point set)
  ; 3 -

  )

(defn display-draggable-lr-ring
  "Displays a draggable ring in the spatial visualization. As the ring is dragged the LR of the ring is updated."
  [ring]
  (viz-helper/clear-geometries)
  (let [ring (d/calculate-derived ring)
        lr (lbs/find-lr ring)
        callback "cmr.spatial.test.lr-binary-search/handle-ring-moved"
        ring (assoc ring
                    :display-options {:callbackFn callback}
                    :draggable true)
        _ (println "Found LR:" (pr-str lr))
        geoms (if lr [ring lr (first (mbr/corner-points lr)) #_(:mbr ring)] [ring (:mbr ring)])]
    (viz-helper/add-geometries geoms)))

(defn handle-ring-moved
  "Callback handler for when the ring is moved. It removes the existing ring and lr and readds it with
  the updated lr."
  [ords-str]
  (let [ords (map #(Double. ^String %) (str/split ords-str #","))
        ring (apply r/ords->ring ords)]
    (display-draggable-lr-ring ring)))