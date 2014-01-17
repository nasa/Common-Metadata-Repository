(ns spatial-testing
  (:require [elastic-connection :as ec]
            [cmr-spatial.point :as point]
            [cmr-spatial.ring :as ring]
            [cmr-spatial.rotations :as rot]
            [cmr-spatial.viz-helper :as viz-helper]))

(def base-points
  "The initial polygon that we will rotate to different areas of the world.  Based on sample granules from
  'IceBridge DMS L1B Geolocated and Orthorectified Images V001'"
  (point/ords->points -92.795184,-69.441759, -92.814511,-69.579957, -92.409809,-69.586403,
                  -92.393254,-69.448158, -92.795184,-69.441759))

(def polygons-per-column
  "Polygons will be generated around the world in twisted 'columns'. This is the number of polygons
  in each column."
  11)

(defn- generate-ring
  "Generates a ring in a position on the earth given by n"
  [n]
  (let [north-dist 14.0
        east-dist 3.9
        column-lon-offset 5
        column-num-offset (Math/floor (/ n polygons-per-column))
        n (mod n polygons-per-column)
        lon-dist (+ (* n east-dist) (* column-lon-offset column-num-offset))
        lat-dist (* n north-dist -1)]
    (ring/ring (rot/rotate-east lon-dist (rot/rotate-north lat-dist base-points)))))

(defn delete-spatial-areas
  "Deletes all the stored spatial areas in elastic."
  []
  (ec/delete-all ec/spatial-index ec/spatial-type))

(defn index-spatial-areas
  "Generates and stores num rings into elastic."
  [num]
  (doseq [n (range num)]
    (ec/index-spatial n (ring/ring->ords (generate-ring n)))))

(defn display-retrieved-items
  "Displays retrieved spatial items from elastic in the spatial viz"
  [items]
  (let [rings (map #(apply ring/ords->ring (:ords %)) items)]
    (viz-helper/add-geometries rings)))

(defn display-spatial-areas-from-elastic []
  (let [items (ec/all-items ec/spatial-index ec/spatial-type)]
    (display-retrieved-items items)))

(comment
  (delete-spatial-areas)

  (index-spatial-areas 100)

  (display-spatial-areas-from-elastic)

  ;; Describing a bounding box with w: -52 n: 30 e: -43 s:27
  (let [search-area (ring/ords->ring -52,30 -52,27, -43,27, -43,30, -52,30)
        results (ec/search-spatial-script (ring/ring->ords search-area))]
    (viz-helper/add-geometries [search-area])
    (display-retrieved-items results))

  ;; Shortcut the elastic part and just generate areas and try them all
  (let [rings (map generate-ring (range 100))
        search-area (ring/ords->ring -52,30 -52,27, -43,27, -43,30, -52,30)
        matches (filter (partial ring/intersects? search-area) rings)]
    (viz-helper/add-geometries matches)
    (viz-helper/add-geometries [search-area]))

  (let [matches [[-84.90212481769589 -84.89349655061902 -84.71338015667583 -84.70523449350628 -41.58647495860211 -41.580041775896014 -41.44824312597533 -41.44182923460395]
                 [-80.98679207151498 -80.97957690594815 -80.82750804914595 -80.82056076376811 -27.586489551125783 -27.580058971828837 -27.448260478756616 -27.44184355524225]
                 [-77.0794842374494 -77.07295175771534 -76.9342420578907 -76.92785588023297 -13.586500779893958 -13.58007220484775 -13.44827385229961 -13.441854592751238]]
        rings (map (partial apply ring/ords->ring) matches)
        search-area (ring/ords->ring -52,30 -52,27, -43,27, -43,30, -52,30)]
    (count (filter (partial ring/intersects? search-area) rings))
    (viz-helper/add-geometries rings))



  )

