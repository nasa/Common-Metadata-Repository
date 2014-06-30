(ns cmr.demos.visual-spatial-search
  "This namespace uses the cmr-vdd-spatial-viz project's spatial visualizations along with system
  integration test functions for inserting granules and searching to provide a way to interactively
  search with arbitrary polygons."
  (:require [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.polygon :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec]
            [clojure.string :as str]
            [cmr.spatial.dev.viz-helper :as viz-helper]
            [cmr.spatial.serialize :as srl]
            [cmr.common.dev.util :as dev-util]
            [cmr.spatial.lr-binary-search :as lbs])
  (:import cmr.spatial.point.Point
           cmr.spatial.ring.Ring
           cmr.spatial.mbr.Mbr
           cmr.spatial.polygon.Polygon))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order."
  [& ords]
  (let [polygon (p/polygon [(apply r/ords->ring ords)])
        outer (-> polygon :rings first derived/calculate-derived)]
    (when (and (:contains-north-pole outer)
               (:contains-south-pole outer))
      (throw (Exception. "Polygon can not contain both north and south pole. Points are likely backwards.")))
    polygon))

(defn shape->lr
  "Converts the shape into an LR and associates a semi transparent blue green with it."
  [shape]
  (assoc (srl/shape->lr shape) :options {:color "AA76775E"}))

(defmulti ords-str->shape
  (fn [spatial-type ords-str]
    spatial-type))

(defmethod ords-str->shape :polygon
  [spatial-type ords-str]
  (let [ords (map #(Double. ^String %) (str/split ords-str #","))]
    (derived/calculate-derived (apply polygon ords))))

(defmethod ords-str->shape :bounding_box
  [spatial-type ords-str]
  (let [[w n e s] (map #(Double. ^String %) (str/split ords-str #","))]
    (m/mbr w n e s)))

(defn handle-search-area-moved
  "This is the VDD callback function. Everytime the search area is moved this will be called with the
  new ordinates of the search area. It removes the existing MBR and LR of the search area, recalculates
  and displays them. It then searches for matches and says the number of hits that match."
  [id-ords-str]
  (println "Search area moved: " id-ords-str)

  (let [[id ords-str] (str/split id-ords-str #":")
        spatial-type (keyword id)
        shape (ords-str->shape spatial-type ords-str)]

    (let [results (search/find-refs :granule {spatial-type (cmr.spatial.codec/url-encode
                                                             shape)})
          {:keys [hits errors]} results]
      (if errors
        (doseq [error errors]
          (dev-util/speak error))
        (dev-util/speak (str hits))))))


(defn visual-interactive-search
  "Execute this to start a visual interactive search. The shapes will be ingested as granules. The
  search area will be displayed on the map as a draggable shape. As the search area is moved
  searches will be executed to find granules that match."
  [shapes search-area]
  (let [shapes (map derived/calculate-derived shapes)]
    (ingest/reset)
    (ingest/create-provider "PROV1")

    (let [coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial :geodetic)}))]
      (doseq [shape shapes]
        (d/ingest "PROV1" (dg/granule coll {:spatial-coverage (dg/spatial shape)}))))
    (index/refresh-elastic-index)

    ;; Save the shapes for display
    (viz-helper/clear-geometries)
    (viz-helper/add-geometries shapes)

    (let [callback "cmr.demos.visual-spatial-search/handle-search-area-moved"
          search-area (-> search-area
                          derived/calculate-derived
                          (update-in [:options]
                                     merge
                                     {:callbackFn callback
                                      :style {:width 5 :color "9918A0ff"}
                                      :draggable true}))]
      (viz-helper/add-geometries [search-area]))))


(comment

  ;; A polygon with a hole
  (let [outer (r/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (r/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (r/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (p/polygon [outer hole1 hole2])
        search-area (polygon 0 0, 1 0, 1 1, 0 0)]
    (visual-interactive-search [polygon-with-holes] search-area))


  ;; A bunch of bounding rectangles
  (let [brs (for [lon (range -170 170 40)
                  lat (range -70 70 20)]
              (m/mbr lon (+ lat 5) (+ lon 10) lat))
        ;; Search area as a polygon
        ; search-area (assoc (polygon 8.38,12.57,11.38,14.8,3.39,22.53,0.35,21.85,8.38,12.57)
        ;                    :options {:id "polygon"})


        ;; Search area as a bounding rectangle
        search-area (assoc (m/mbr -5 5 5 -5) :options {:id "bounding_box"})]
    (visual-interactive-search brs search-area))

  (let [touches-np (m/mbr 45 90 55 70)
        touches-sp (m/mbr -160 -70 -150 -90)
        across-am-br (m/mbr 170 10 -170 -10)
        normal-br (m/mbr 10 10 20 0)

        ;; Polygons
        wide-north (polygon -70 20, 70 20, 70 30, -70 30, -70 20)
        wide-south (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30)
        across-am-poly (polygon 170 35, -175 35, -170 45, 175 45, 170 35)
        on-np (polygon 45 85, 135 85, -135 85, -45 85, 45 85)
        on-sp (polygon -45 -85, -135 -85, 135 -85, 45 -85, -45 -85)
        normal-poly (polygon -20 -10, -10 -10, -10 10, -20 10, -20 -10)

        ;; polygon with holes
        outer (r/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (r/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (r/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (p/polygon [outer hole1 hole2])

        search-area (assoc (polygon -6.45,-3.74,12.34,-4.18,12,9.45,-6.69,9.2,-6.45,-3.74)
                           :options {:id "polygon"})
        ; search-area (assoc (m/mbr -23.43 5 25.54 -6.31) :options {:id "bounding_box"})

        ]
    (visual-interactive-search [touches-sp
                                across-am-br
                                touches-np
                                normal-br
                                wide-north
                                wide-south
                                across-am-poly
                                on-np
                                on-sp
                                normal-poly
                                polygon-with-holes] search-area))


  ;; A set of polygons and a search area that can be displayed.
  (let [polygon-ne (polygon 20 10, 30 20, 10 20, 20 10)
        polygon-se (polygon 30 -20, 20 -10, 10 -20, 30 -20)
        polygon-sw (polygon -20 -20, -10 -10, -30 -10,-20 -20)
        polygon-nw (polygon -20 10, -30 20, -30 10, -20 10)
        polygon-north-pole (polygon 45, 80, 135, 80, -135, 80, -45, 80,  45 80)
        polygon-south-pole (polygon -45 -80, -135 -80, 135 -80, 45 -80, -45 -80)
        polygon-antimeridian (polygon 135 -10, -135 -10, -135 10, 135 10, 135 -10)
        polygon-near-sp (polygon 168.1075 -78.0047, 170.1569,-78.4112, 172.019,-78.0002, 169.9779,-77.6071, 168.1075 -78.0047)

        ;; Search area as a polygon
        ; search-area (assoc (polygon 8.38,12.57,11.38,14.8,3.39,22.53,0.35,21.85,8.38,12.57)
        ;                    :options {:id "polygon"})


        ;; Search area as a bounding rectangle
        search-area (assoc (m/mbr -5 5 5 -5) :options {:id "bounding_box"})
        ]

    (visual-interactive-search [polygon-ne
                                polygon-se
                                polygon-sw
                                polygon-nw
                                polygon-north-pole
                                polygon-south-pole
                                polygon-antimeridian
                                polygon-near-sp]
                               search-area))


  )



