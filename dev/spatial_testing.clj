(ns spatial-testing
  (:require [elastic-connection :as ec]
            [cmr-spatial.point :as point]
            [cmr-spatial.ring :as ring]
            [cmr-spatial.rotations :as rot]
            [cmr-spatial.viz-helper :as viz-helper]
            [cmr.es-spatial-plugin.spatial-script-helper :as spatial-script-helper]))

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
    (let [ords (ring/ring->ords (generate-ring n))
          ords (spatial-script-helper/ordinates->stored-ordinates ords)]
      (ec/index-spatial n ords))))

(defn display-retrieved-items
  "Displays retrieved spatial items from elastic in the spatial viz"
  [items]
  (let [rings (map #(->> %
                        :ords
                        spatial-script-helper/stored-ordinates->ordinates
                        (apply ring/ords->ring)) items)]
    (viz-helper/add-geometries rings)))

(defn display-spatial-areas-from-elastic []
  (let [items (ec/all-items ec/spatial-index ec/spatial-type)]
    (display-retrieved-items items)))

(defn search-and-display
  "Searches for results with the given ring then displays the ring and the areas found."
  [constructor & ords]
  (let [results (ec/search-spatial-script ords)]
    (viz-helper/add-geometries [(apply constructor ords)])
    (display-retrieved-items results)))

(comment
  (delete-spatial-areas)

  (index-spatial-areas 5000)

  (display-spatial-areas-from-elastic)

  ;; Describing a bounding box with w: -55.3 n: 30 e: -43 s:27
  (search-and-display ring/ords->ring -55.3,30 -55.3,27, -43,27, -43,30, -55.3,30)

  ;; thin area that won't have points inside another ring.
  (search-and-display ring/ords->ring  -60.455,28.523 -60.467,28.46 -60.162,28.455, -60.17,28.525 -60.455,28.523)

  (search-and-display point/point -50.301,28.498)


  ;; Shortcut the elastic part and just generate areas and try them all
  (let [rings (map generate-ring (range 100))
        search-area (ring/ords->ring -55.3,30 -55.3,27, -43,27, -43,30, -55.3,30)
        matches (filter (partial ring/intersects? search-area) rings)]
    (viz-helper/add-geometries matches)
    (viz-helper/add-geometries [search-area]))




  )

