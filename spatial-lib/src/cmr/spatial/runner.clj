(ns cmr.spatial.runner
  "Create a command line interface to expose useful api calls for working with spatial data."
  (:require
   [cheshire.core :as json]
   [cmr.spatial.lr-binary-search :as lr-bin]
   [cmr.spatial.ring-relations :as ring-rel])
  (:gen-class))

(defn parse-polygon
  "Take a raw string in the style of POLYGON((x1 y1, x2 y2, ...)) and return a vector of vectors of
   doubles"
  [polygon-str]
  (vec (flatten (->> polygon-str
                     (re-seq #"-?\d+\.\d+")
                     (map #(Double/parseDouble %))
                     (partition 2)
                     (mapv vec)))))

(defn create-wkt-bbox
  "Create a string in for a bbox in the WKT format."
  ([obj]
   (create-wkt-bbox (:west obj) (:east obj) (:south obj) (:north obj)))
  ([west east south north]
  (str "POLYGON(("
       west " " south ", "
       east " " south ", "
       east " " north ", "
       west " " north ", "
       west " " south
       "))")))

(defn polygon-string->box
  "Take a raw string in the POLYGON(()) style and turn it into a bbox, a dictionary with east,
  west, north, south edges."
  [raw-string]
  (let [polygon-str raw-string
        polygon (parse-polygon polygon-str)
        polygon-obj (ring-rel/ords->ring :cartesian polygon)
        lr (lr-bin/find-lr polygon-obj)]
    {:west (:west lr) :east (:east lr) :south (:south lr) :north (:north lr)}))

;; A command line interface to run any utilitity in the spatial-lib package. Currently only
;; exposing the find-lr function.
(defn -main [& args]
  (if (empty? args)
    (println "Usage: action <polygon-coordinates>")
    (println
     (case (first args)
      "lr-wkt" (create-wkt-bbox (polygon-string->box (second args)))
      "lr-json" (json/generate-string (polygon-string->box (second args)))
      "Unknown action, try: lr-wkt or lr-json"))))

(comment

  (def polygon-string
    (str "POLYGON ((-124.409202 32.531669, -114.119061 32.531669, -114.119061 41.99954,"
         "-124.409202 41.99954, -124.409202 32.531669))"))

  ;; Run the command line interface with different parameters
  (-main)
  (-main "wrong")
  (-main "lr-json" polygon-string)
  (-main "lr-wkt" (slurp (io/resource "multipolygon.txt")))

  ;; try out the parse function
  (parse-polygon polygon-string)

  ;; call the find-lr directly and see what it returns
  (lr-bin/find-lr (ring-rel/ords->ring :cartesian [0 0, 10 0, 10 10, 0 10, 0 0]))

  )
