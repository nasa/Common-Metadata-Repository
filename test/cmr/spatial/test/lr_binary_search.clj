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

;; TODO still a work in progress
#_(defspec all-rings-have-lrs {:times 1000 :printer-fn sgen/print-failed-ring}
  (for-all [ring sgen/rings]
    (let [lr (lbs/find-lr ring)]
      (and lr
           (r/covers-br? ring lr)))))

(comment
  (def ring (d/calculate-derived
              (first (gen/sample sgen/rings 1))))

  ;; TODO

  (def ring (d/calculate-derived (r/ords->ring -0.06,2.68,2.53,1.44,-0.01,3.79,-2.2,2.28,-0.06,2.68)))


  (def lr #cmr.spatial.mbr.Mbr{:west -1.200695979893208, :north 2.969781649184588, :east 0.8782859247922897, :south 2.593485107421875})

  (r/covers-br? ring lr)
  (lbs/mid-br #{:west :north :east :south} lr (:mbr ring))



  (lbs/find-lr ring)
  (r/covers-br? ring (lbs/find-lr ring))

  (viz-helper/add-geometries [ring])
  (display-draggable-lr-ring ring)


  (a/midpoint (a/arc (p/point 1 90) (p/point 1 1)))


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