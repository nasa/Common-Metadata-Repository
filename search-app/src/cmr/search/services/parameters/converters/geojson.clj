(ns cmr.search.services.parameters.converters.geojson
  "Contains functions to sanitize geojson files so they don't break our parsing"
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [cmr.common.services.errors :as errors]))

(defn- remove-features-with-no-geometry
  "Removes features that have null geometry value"
  [geojson]
  (let [features (get geojson "features" [])
        geom-features (filter #(get % "geometry") features)]
    (if (seq geom-features)
      (assoc geojson "features" geom-features)
      (errors/throw-service-error :bad-request "Shapefile has no features"))))

(def geojson-sanitizers
  "Functions that sanitize geojson files (represented as Clojure data structures"
  [remove-features-with-no-geometry])

(defn sanitize-geojson
  "Remove/fix and entries in a GeoJSON file that would break later parsing. The GeoJSON file
  will be overwritten by the santized version. `file` is a Java File object."
  [file]
  (let [json-str (slurp file)
        geojson (json/parse-string json-str)
        sanitized-map (reduce (fn [s, f] (f s))
                              geojson
                              geojson-sanitizers)]
    (json/generate-stream sanitized-map (io/writer file))))
