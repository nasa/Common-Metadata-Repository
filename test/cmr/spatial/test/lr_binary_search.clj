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


;; TODO still working on the LR algorithm
#_(defspec all-rings-have-lrs {:times 100 :printer-fn sgen/print-failed-ring}
  (for-all [ring sgen/rings]
    (let [lr (lbs/find-lr ring)]
      (and lr
           (r/covers-br? ring lr)))))
(comment
  (def ring (r/ring [(p/point 1.0 1.0) (p/point 1.0 4.0) (p/point -1.0 1.0) (p/point -2.0 1.0) (p/point -1.0 0.0) (p/point 1.0 1.0)]))

  (lbs/find-lr ring)

  (display-draggable-lr-ring ring)
)

(defn display-draggable-lr-ring
  "Displays a draggable ring in the spatial visualization. As the ring is dragged the LR of the ring is updated."
  [ring]
  (viz-helper/clear-geometries)
  (let [lr (lbs/find-lr ring)
        callback "cmr.spatial.test.lr-binary-search/handle-ring-moved"
        ring (assoc ring
                    :display-options {:callbackFn callback}
                    :draggable true)
        geoms (if lr [ring lr] [ring])]
    (viz-helper/add-geometries geoms)))

(defn handle-ring-moved
  "Callback handler for when the ring is moved. It removes the existing ring and lr and readds it with
  the updated lr."
  [ords-str]
  (let [ords (map #(Double. ^String %) (str/split ords-str #","))
        ring (apply r/ords->ring ords)]
    (display-draggable-lr-ring ring)))