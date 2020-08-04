(ns cmr.search.results-handlers.orbit-swath-results-helper
  "Provides helper functions for result handlers that need to return orbit swaths as polygons in
  results"
  (:require [cmr.spatial.orbits.swath-geometry :as swath]
            [cmr.search.services.query-helper-service :as query-helper]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def orbit-elastic-fields
  "This is a set of the elastic fields that need to be retrieved for adding orbit swaths"
  #{"collection-concept-id"
    "start-date"
    "end-date"
    "orbit-calculated-spatial-domains-json"
    "orbit-asc-crossing-lon"})

(defn- elastic-result-has-orbit-spatial?
  "Returns true if the elastic result has orbit spatial fields."
  [elastic-result]
  (some? (get-in elastic-result [:_source :orbit-asc-crossing-lon])))

(defn- elastic-result->collection-concept-id
  [elastic-result]
  (get-in elastic-result [:_source :collection-concept-id]))

(defn get-orbits-by-collection
  "Gets a map of collection concept id to collection orbit parameters. It takes all of the
  granule elastic matches that were found so that the collections retrieved can be limited
  only to the ones that are required. This map is needed when converting the elastic result
  to swatch shapes."
  [context elastic-matches]
  (let [collection-orbits (->> elastic-matches
                               ;; Limit to those with some orbit results
                               (filter elastic-result-has-orbit-spatial?)
                               ;; Get the set of distinct collection concept ids
                               (map elastic-result->collection-concept-id)
                               distinct
                               ;; Look up the orbit parameters for those collections
                               (query-helper/collection-orbit-parameters context))]
    ;; Combine it into a map of concept id to the orbit parameters
    (zipmap (map :concept-id collection-orbits) collection-orbits)))

(def ocsd-fields
  "The fields for orbit calculated spatil domains, in the order that they are stored in the json
  string in the index."
  [:equator-crossing-date-time
   :equator-crossing-longitude
   :orbital-model-name
   :orbit-number
   :start-orbit-number
   :stop-orbit-number])

(defn ocsd-json->map
  "Conver the orbit calculated spatial domain json string from elastic into a map. The string
  is stored internally as a vector of values."
  [json-str]
  (let [value-array (json/decode json-str)]
    (zipmap ocsd-fields value-array)))

(defn elastic-result->swath-shapes
  "Returns the orbit shapes generated from the elastic results. Will return nil if this elastic result
  does not have orbit data."
  [orbits-by-collection elastic-result]
  (let [{collection-concept-id :collection-concept-id
         start-date :start-date
         end-date :end-date
         orbit-calculated-spatial-domains-json :orbit-calculated-spatial-domains-json
         orbit-asc-crossing-lon :orbit-asc-crossing-lon} (:_source elastic-result)
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))
        orbit-calculated-spatial-domains (map ocsd-json->map orbit-calculated-spatial-domains-json)]
    (when (and orbit-asc-crossing-lon
               (not-empty orbit-calculated-spatial-domains))
      (swath/to-polygons
        (orbits-by-collection collection-concept-id)
        orbit-asc-crossing-lon
        orbit-calculated-spatial-domains
        start-date
        end-date))))
