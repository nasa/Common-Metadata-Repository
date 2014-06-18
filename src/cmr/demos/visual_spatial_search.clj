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
            [cmr.spatial.lr-binary-search :as lbs]))

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

(defn handle-search-area-moved
  "This is the VDD callback function. Everytime the search area is moved this will be called with the
  new ordinates of the search area. It removes the existing MBR and LR of the search area, recalculates
  and displays them. It then searches for matches and says the number of hits that match."
  [ords-str]
  (println "Search area moved: " ords-str)

  (let [ords (map #(Double. ^String %) (str/split ords-str #","))
        ring (derived/calculate-derived (apply polygon ords))
        mbr (assoc-in (srl/shape->mbr ring)
                      [:options :id] "search-mbr")
        lr (assoc-in (shape->lr ring) [:options :id] "search-lr")]

    (viz-helper/remove-geometries ["search-lr" "search-mbr"])
    (viz-helper/add-geometries [mbr lr])

    (let [results (search/find-refs :granule {:polygon (cmr.spatial.codec/url-encode
                                                         (p/polygon [ring]))})
          hits (:hits results)]
      (dev-util/speak (str hits)))))


(defn visual-interactive-search
  "Execute this to start a visual interactive search. The polygons will be ingested as granules. The
  search area will be displayed on the map as a draggable ring. As the search area is moved
  searches will be executed to find granules that match."
  [polygons search-area]
  (let [polygons (map derived/calculate-derived polygons)]
    (ingest/reset)
    (ingest/create-provider "PROV1")

    (let [coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial :geodetic)}))]
      (doseq [polygon polygons]
        (d/ingest "PROV1" (dg/granule coll {:spatial-coverage (dg/spatial polygon)}))))
    (index/refresh-elastic-index)

    ;; Save the polygons for display
    (viz-helper/clear-geometries)
    (viz-helper/add-geometries
      (concat polygons
              (map srl/shape->mbr polygons)
              (map shape->lr polygons)))

    (let [callback "cmr.demos.visual-spatial-search/handle-search-area-moved"
          search-area (-> search-area
                          derived/calculate-derived
                          (assoc
                            :options {:callbackFn callback
                                      :style {:width 5 :color "9918A0ff"}
                                      :draggable true}))
          mbr (assoc-in (srl/shape->mbr search-area) [:options :id] "search-mbr")
          lr (assoc-in (shape->lr search-area) [:options :id] "search-lr")]
      (viz-helper/add-geometries [search-area mbr lr]))))


(comment

  ;; A polygon with a hole
  (let [outer (r/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (r/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (r/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (p/polygon [outer hole1 hole2])
        search-area (polygon 0 0, 1 0, 1 1, 0 1, 0 0)]
    (visual-interactive-search [polygon-with-holes] search-area))


  ;; A set of polygons and a search area that can be displayed.
  (let [polygon-ne (polygon 20 10, 30 20, 10 20, 20 10)
        polygon-se (polygon 30 -20, 20 -10, 10 -20, 30 -20)
        polygon-sw (polygon -20 -20, -10 -10, -30 -10,-20 -20)
        polygon-nw (polygon -20 10, -30 20, -30 10, -20 10)
        polygon-north-pole (polygon 45, 80, 135, 80, -135, 80, -45, 80,  45 80)
        polygon-south-pole (polygon -45 -80, -135 -80, 135 -80, 45 -80, -45 -80)
        polygon-antimeridian (polygon 135 -10, -135 -10, -135 10, 135 10, 135 -10)
        polygon-near-sp (polygon 168.1075 -78.0047, 170.1569,-78.4112, 172.019,-78.0002, 169.9779,-77.6071, 168.1075 -78.0047)
        search-area (polygon 8.38,12.57,11.38,14.8,3.39,22.53,0.35,21.85,8.38,12.57)]

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



