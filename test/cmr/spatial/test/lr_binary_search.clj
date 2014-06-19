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
            [cmr.spatial.mbr :as m]
            [cmr.spatial.derived :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.lr-binary-search :as lbs]
            [cmr.spatial.relations :as relations]
            [cmr.spatial.dev.viz-helper :as viz-helper])
  (:import cmr.spatial.point.Point))

(defspec all-rings-have-lrs {:times 100 :printer-fn sgen/print-failed-ring}
  (for-all [ring (sgen/rings)]
    (let [lr (lbs/find-lr ring false)]
      (and lr
           (r/covers-br? ring lr)))))

(defn polygon-has-valid-lr?
  "Returns true if the polygon has a valid lr."
  [polygon]
  (let [lr (lbs/find-lr polygon false)]
    (and lr
         (poly/covers-br? polygon lr))))

(defspec simple-polygon-with-holes-has-lr {:times 100 :printer-fn sgen/print-failed-ring}
  (let [boundary (d/calculate-derived (r/ords->ring 0 0, 10 0, 10 10, 0 10, 0 0))]
    (for-all [hole (sgen/rings-in-ring boundary)]
      (let [polygon (d/calculate-derived (poly/polygon [boundary hole]))]
        (polygon-has-valid-lr? polygon)))))

(defspec all-polygons-with-holes-have-lrs {:times 100 :printer-fn sgen/print-failed-polygon}
  (for-all [polygon (gen/no-shrink sgen/polygons-with-holes)]
    (polygon-has-valid-lr? polygon)))

(deftest example-polygons-have-lrs
  (let [polygons-ordses [[[-60.25739 -68.00377, -59.64225 -68.05437, -59.57187 -68.05437,
                           -59.5413 -68.05092, -59.49545 -68.04172, -59.49851 -68.03482,
                           -59.51381 -68.03597, -59.59335 -68.04747, -59.93609 -68.01872,
                           -60.21455 -67.99917, -60.25739 -67.99917, -60.25739 -68.00377]]]]
    (doseq [polygon-ordses polygons-ordses]
      (let [polygon (d/calculate-derived (poly/polygon (mapv (partial apply r/ords->ring)
                                                             polygon-ordses)))]
        (is (polygon-has-valid-lr? polygon))))))

(comment
  ;; Visualization samples and helpers

  (display-draggable-lr-polygon
    (poly/polygon [(r/ords->ring 0,0, 4,0, 6,5, 2,5, 0,0)
                   (r/ords->ring 4,3.34, 2,3.34, 3,1.67, 4,3.34)]))

  ;; Polygon with multiple inner rings

  (let [ordses [[0,0, 4,0, 6,5, 2.14,4.86, -0.92,4.75, 0,0]
                [2.34,4.22, 1.22,3.92, -0.11,4.21, 0.7,2.57, 2.34,4.22]
                [3.7,3.33, 1.25,1.04, 3.37,0.61, 3.7,3.33]]
        polygon (poly/polygon (mapv (partial apply r/ords->ring)
                                    ordses))]
    (display-draggable-lr-polygon polygon))


  (def ring (d/calculate-derived
              (first (gen/sample (sgen/rings) 1))))

  ;; Samples

  ;; Normal
  (display-draggable-lr-ring
    (r/ords->ring 0,0, 4,0, 6,5, 2,5, 0,0))

  ;; Very larges
  (display-draggable-lr-ring
    (r/ords->ring -89.9 -45, 89.9 -45, 89.9 45, -89.9 45, -89 -45))

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

  (defn measure-find-lr-performance
    [n-points]
    (let [ring (d/calculate-derived (br->ring-with-n-points (m/mbr -170 45 170 -45) n-points))]
      (with-progress-reporting
        (bench
          (lbs/find-lr ring)))))

  (measure-find-lr-performance 4) ; 1.4 ms
  (measure-find-lr-performance 50) ; 5 ms
  (measure-find-lr-performance 2000) ; 301 ms

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for visualizing and interacting with polygons to show LRs

(def displaying-polygon-atom
  "Holds onto the current polygon being displayed. A single ring is sent back from the visualizztion
  when it is dragged. We update this polygon and use it to find the current LR."
  (atom nil))

(comment
  (mapv r/ring->ords (:rings @displaying-polygon-atom))

  )

(defn display-draggable-lr-polygon
  "Displays a draggable polygon in the spatial visualization. As the polygon is dragged the LR of the polygon is updated."
  [polygon]
  (viz-helper/clear-geometries)
  (let [polygon (d/calculate-derived polygon)
        lr (lbs/find-lr polygon false)
        callback "cmr.spatial.test.lr-binary-search/handle-polygon-moved"
        ;; Add options for polygon to be displayed.
        polygon (-> polygon
                    (assoc :options {:callbackFn callback
                                     :draggable true})
                    (update-in [:rings] (fn [rings]
                                          (vec (map-indexed
                                                 (fn [i ring]
                                                   (assoc-in ring [:options :id] i))
                                                 rings)))))
        _ (println "Found LR:" (pr-str lr))]

    (reset! displaying-polygon-atom polygon)
    (viz-helper/add-geometries [polygon])
    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))

(defn handle-polygon-moved
  "Callback handler for when the polygon is moved. It removes the existing polygon and lr and readds it with
  the updated lr."
  [ring-str]
  (let [[^String id ords-str] (str/split ring-str #":")
        ords (map #(Double. ^String %) (str/split ords-str #","))
        ring (d/calculate-derived (apply r/ords->ring ords))
        polygon (swap! displaying-polygon-atom (fn [polygon]
                                                 (assoc-in polygon [:rings (Long. id)] ring)))
        lr (lbs/find-lr polygon false)
        _ (println "Found LR:" (pr-str lr))]

    (viz-helper/remove-geometries ["lr"])

    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for visualizing and interacting with rings to show LRs

(defn display-draggable-lr-ring
  "Displays a draggable ring in the spatial visualization. As the ring is dragged the LR of the ring is updated."
  [ring]
  (viz-helper/clear-geometries)
  (let [ring (d/calculate-derived ring)
        lr (lbs/find-lr ring false)
        callback "cmr.spatial.test.lr-binary-search/handle-ring-moved"
        ring (assoc ring
                    :options {:callbackFn callback
                              :draggable true})
        _ (println "Found LR:" (pr-str lr))]
    (viz-helper/add-geometries [ring])
    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))

(defn handle-ring-moved
  "Callback handler for when the ring is moved. It removes the existing ring and lr and readds it with
  the updated lr."
  [ords-str]
  (let [ords (map #(Double. ^String %) (str/split ords-str #","))
        ring (d/calculate-derived (apply r/ords->ring ords))
        lr (lbs/find-lr ring false)]
    (viz-helper/remove-geometries ["lr"])
    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))



