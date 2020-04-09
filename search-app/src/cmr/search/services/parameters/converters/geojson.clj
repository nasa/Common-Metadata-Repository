(ns cmr.search.services.parameters.converters.geojson
  "Contains functions to sanitize geojson files so they don't break our parsing"
  (:require 
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [cmr.common.config :as cfg :refer [defconfig]]
    [cmr.common.log :refer [debug]]
    [cmr.common.mime-types :as mt]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.params :as p]
    [cmr.common.services.errors :as errors]
    [cmr.search.models.query :as qm]
    [cmr.search.services.parameters.converters.geometry :as geo]
    [cmr.common.util :as util]))


(defn- remove-features-with-no-geometry
  "Removes featgures that have null geometry value"
  [geojson]
  (let [features (get geojson "features" [])
        geom-features (filter #(get % "geometry") features)]
     (assoc geojson "features" geom-features)))

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
  
  
  
  
