(ns cmr.nlp.geonames
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.nlp.util :as util]))

(def gazetteer-columns
  {0 :geonameid
   1 :name
   2 :asciiname
   3 :alternatenames
   4 :latitude
   5 :longitude
   6 :feature-class
   7 :feature-code
   8 :country-code
   9 :cc2
   10 :admin1-code
   11 :admin2-code
   12 :admin3-code
   13 :admin4-code
   14 :population
   15 :elevation
   16 :dem
   17 :timezone
   18 :modification-date})

(def shapes-columns
  {0 :geonameid
   1 :geojson})

(defn add-gazetteer-field
  [idx value]
  [(get gazetteer-columns idx) (string/trim value)])

(defn add-gazetteer-fields
  [row]
  (->> row
       (map-indexed add-gazetteer-field)
       (into {})))

(defn read-gazetteer
  []
  (->> "allCountries.txt"
       util/read-geonames
       (map add-gazetteer-fields)))

(defn add-shapes-field
  [idx value]
  [(get shapes-columns idx) (json/parse-string value true)])

(defn add-shapes-fields
  [row]
  (->> row
       (map-indexed add-shapes-field)
       (into {})))

(defn read-shapes
  []
  (->> "shapes_all_low.txt"
       util/read-geonames
       rest
       (map add-shapes-fields)))
