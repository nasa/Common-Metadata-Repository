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



  (def ring (d/calculate-derived (r/ring [(p/point -1.0 1.0)
                                          (p/point -1.0 -1.0)
                                          (p/point 1.0 1.0)
                                          (p/point -82.0 1.125)
                                          (p/point 102.0 -1.0)
                                          (p/point -79.0 1.0)
                                          (p/point -1.0 1.0)])))

  (lbs/find-lr ring)

  (r/covers-br? ring (lbs/find-lr ring))

  (viz-helper/clear-geometries)
  (viz-helper/add-geometries [ring])

  (display-draggable-lr-ring ring)


  (a/midpoint (a/arc (p/point 1 90)
                     (p/point 1 1)))


  (viz-helper/clear-geometries)
  (viz-helper/add-geometries [ring])

  (viz-helper/add-geometries
    [(lbs/mbr->vert-dividing-arc (:mbr ring))])


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