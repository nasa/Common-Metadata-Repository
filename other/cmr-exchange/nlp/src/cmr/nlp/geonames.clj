(ns cmr.nlp.geonames
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.nlp.util :as util]))

(def index-name "geonames")
(def doc-type "geoname")

(def gazetteer-fields
  "Column names defined for the main Geonames table.

  For more details, see: http://download.geonames.org/export/dump/readme.txt."
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

(def shapes-fields
  "Columns names defined for the shapes lookup table
  (geonames-to-polygon-coordinates).

  For more details, see: http://download.geonames.org/export/dump/readme.txt.

  Note that the readme.txt file does not explicitly reference ``, but rather
  `shapes_simplified_low.txt`; they use the same column names."
  {0 :geonameid
   1 :geojson})

(defn add-gazetteer-field
  "Given an index and a value, return a tuple of field name and value."
  [idx value]
  [(get gazetteer-fields idx) (string/trim value)])

(defn add-gazetteer-fields
  "Given a row of data that has data for all the fields, generate a hash-map
  with the data mapped to field names."
  [row]
  (->> row
       (map-indexed add-gazetteer-field)
       (into {})))

(defn read-gazetteer
  "Read the `allCountries` Geonames gazeteer file from the classpath and
  convert the tab-delimited data to a lazy sequence of maps, where the keys are
  the column names of the gazeteer file."
  []
  (->> "allCountries.txt"
       util/read-geonames
       (map add-gazetteer-fields)))

(defn batch-read-gazetteer
  "Produce a lazy sequence of gazeteer file lines in batches of the given size."
  [batch-size]
  (partition-all batch-size (read-gazetteer)))

(defn add-shapes-field
  "Given an index and a value, return a tuple of field name and value."
  [idx value]
  [(get shapes-fields idx) (json/parse-string value true)])

(defn add-shapes-fields
  "Given a row of data that has data for all the fields, generate a hash-map
  with the data mapped to field names."
  [row]
  (->> row
       (map-indexed add-shapes-field)
       (into {})))

(defn read-shapes
  "Read the `shapes_all_low` file that provide polygons mapped to geonameid.
  Creates a lazy sequence of maps where the keys of the maps are the columns
  from the tab-delimited source data file."
  []
  (->> "shapes_all_low.txt"
       util/read-geonames
       rest
       (map add-shapes-fields)))

(defn shapes-lookup
  "Creates a hash-map of shapes in GeoJSON format keyed off of Geonames ids.
  This is used to add any applicable Geonames polygon coordinates when indexing
  Geonames data in Elasticsearch."
  []
  (->> (read-shapes)
       (map (fn [x] [(:geonameid x) (:geojson x)]))
       (into {})))
