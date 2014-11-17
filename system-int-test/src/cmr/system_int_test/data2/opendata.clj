(ns cmr.system-int-test.data2.opendata
  "Contains functions for parsing opendata results."
  (:require [clojure.data.xml :as x]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.line-string :as l]
            [clojure.string :as str]
            [cmr.spatial.relations :as r]
            [camel-snake-kebab :as csk]
            [cmr.umm.spatial :as umm-s]
            [cmr.umm.echo10.spatial :as echo-s]
            [clojure.test]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [cmr.search.results-handlers.opendata-results-handler :as odrh]
            [cmr.indexer.data.concepts.science-keyword :as sk]
            [cmr.umm.related-url-helper :as ru]
            [cmr.umm.start-end-date :as sed])
  (:import cmr.umm.collection.UmmCollection
           cmr.spatial.mbr.Mbr))

(defn parse-opendata-result
  "Returns the opendata result from a json string"
  [concept-type json-str]
  (let [json-struct (json/decode json-str true)]
    json-struct))

(defn collection->expected-opendata
  [collection]
  (let [{:keys [short-name keywords project-sn summary entry-title
                access-value concept-id related-urls contact-name contact-email
                data-format]} collection
        spatial-representation (get-in collection [:spatial-coverage :spatial-representation])
        update-time (get-in collection [:data-provider-timestamps :update-time])
        insert-time (get-in collection [:data-provider-timestamps :insert-time])
        temporal (:temporal collection)
        start-date (sed/start-date :collection temporal)
        end-date (sed/end-date :collection temporal)
        start-date (when start-date (str/replace (str start-date) #"\.000Z" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\.000Z" "Z"))
        shapes (map (partial umm-s/set-coordinate-system spatial-representation)
                    (get-in collection [:spatial-coverage :geometries]))
        distribution (not-empty (odrh/distribution related-urls))]
    (util/remove-nil-keys {:title entry-title
                           :description summary
                           :keyword (not-empty (sk/flatten-science-keywords collection))
                           :modified (str update-time)
                           :publisher odrh/PUBLISHER
                           :contactPoint contact-name
                           :mbox contact-email
                           :identifier concept-id
                           :accessLevel "public"
                           :bureauCode [odrh/BUREAU_CODE]
                           :programCode [odrh/PROGRAM_CODE]
                           :accessURL (:accessURL (first distribution))
                           :format (:format (first distribution))
                           :spatial (odrh/spatial shapes false)
                           :temporal (odrh/temporal start-date end-date)
                           :theme (not-empty (str/join "," project-sn))
                           :distribution distribution
                           :landingPage (odrh/landing-page concept-id)
                           :language [odrh/LANGUAGE_CODE]
                           :references (not-empty (map :url related-urls))
                           :issued (str insert-time)})))

(defn collections->expected-opendata
  [collections]
  {:status 200
   :results (set (map collection->expected-opendata collections))})

(defn assert-collection-opendata-results-match
  "Returns true if the opendata results are for the expected items"
  [collections actual-result]
  (clojure.test/is (= (collections->expected-opendata collections)
                      (update-in actual-result [:results] set))))