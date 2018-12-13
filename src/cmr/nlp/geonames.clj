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
   6 :feature_class
   7 :feature_code
   8 :country_code
   9 :cc2
   10 :admin1_code
   11 :admin2_code
   12 :admin3_code
   13 :admin4_code
   14 :population
   15 :elevation
   16 :dem
   17 :timezone
   18 :modification_date})

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

(defn batch-read-gazetteer
  [batch-size]
  (partition batch-size (read-gazetteer)))

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

(defn shapes-lookup
  []
  (->> (read-shapes)
       (map (fn [x] [(:geonameid x) (:geojson x)]))
       (into {})))
