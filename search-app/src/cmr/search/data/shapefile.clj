(ns cmr.search.data.shapefile
  "Functions related to handling shapefiles"
  (:require
   [clojure.java.io :as io]
   [cmr.common.config :refer [defconfig]]))

(defmulti parse-shapefile
  "Parse a shapefile into JTS Geoemtry. Takes a map of the form:
  {:content <path-to-file> :content-type <type string>}"
  (fn [shapefile] (keyword (:content-type shapefile))))

(defmethod parse-shapefile :esri
  [shapefile]
  (println "Parsing ESRI shapefile"))

