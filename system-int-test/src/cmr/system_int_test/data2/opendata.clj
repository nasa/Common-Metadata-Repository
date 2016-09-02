(ns cmr.system-int-test.data2.opendata
  "Contains functions for parsing opendata results."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [clojure.test :refer [is]]
   [clojure.test]
   [cmr.common.util :as util]
   [cmr.indexer.data.concepts.collection :as c]
   [cmr.indexer.data.concepts.science-keyword :as sk]
   [cmr.search.results-handlers.opendata-results-handler :as odrh]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.relations :as r]
   [cmr.spatial.ring-relations :as rr]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.umm.echo10.spatial :as echo-s]
   [cmr.umm.related-url-helper :as ru]
   [cmr.umm.start-end-date :as sed]
   [cmr.umm.umm-spatial :as umm-s])
  (:import
   (cmr.spatial.mbr Mbr)
   (cmr.umm.umm_collection UmmCollection)))

(defn parse-opendata-result
  "Returns the opendata result from a json string"
  [concept-type json-str]
  (let [json-struct (json/decode json-str true)]
    json-struct))

(defn personnel->contact-email
  "Returns a contact email from the personnel record or the default if one
  is not available."
  [personnel]
  (or (when-let [contacts (:contacts personnel)]
        (when-let [contact (first (filter #(= :email (:type %)) contacts))]
          (:value contact)))
      odrh/DEFAULT_CONTACT_EMAIL))

(defn- contact-point
  "Creates the contactPoint field including the name and email address"
  [personnel]
  {:fn (odrh/personnel->contact-name personnel)
   :hasEmail (str "mailto:" (personnel->contact-email personnel))})

(defn collection->expected-opendata
  "Convert to expcted opendata. First convert to native format metadata then back to UMM to mimic
  ingest. If umm-json leave as is since parse-concept will convert to echo10."
  [collection]
  (let [{:keys [format-key concept-id data-format provider-id]} collection
        collection (data-core/mimic-ingest-retrieve-metadata-conversion collection)
        {:keys [short-name keywords projects related-urls summary entry-title organizations
                access-value personnel]} collection
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
        distribution (not-empty (odrh/distribution related-urls))
        project-sn (not-empty (map :short-name projects))
        personnel (c/person-with-email personnel)
        contact-point (contact-point personnel)
        archive-center (:org-name (first (filter #(= :archive-center (:type %)) organizations)))]
    (util/remove-nil-keys {:title entry-title
                           :description summary
                           :keyword (conj (sk/flatten-science-keywords collection)
                                          "NGDA"
                                          "National Geospatial Data Asset")
                           :modified (str update-time)
                           :publisher (odrh/publisher provider-id archive-center)
                           :contactPoint contact-point
                           :identifier concept-id
                           :accessLevel "public"
                           :bureauCode [odrh/BUREAU_CODE]
                           :programCode [odrh/PROGRAM_CODE]
                           :spatial (odrh/spatial shapes)
                           :temporal (odrh/temporal start-date end-date)
                           :theme (conj project-sn "geospatial")
                           :distribution distribution
                           :landingPage (odrh/landing-page concept-id)
                           :language [odrh/LANGUAGE_CODE]
                           :references (not-empty (map :url related-urls))
                           :issued (str insert-time)})))

(defn collections->expected-opendata
  [collections]
  {:status 200
   :results {:conformsTo odrh/OPENDATA_SCHEMA
             :dataset (map collection->expected-opendata collections)}})

(defn- opendata-results-map->opendata-results-map-using-sets
  "Converts all of the collections within an opendata results map to sets. This allows maps to be
  compared such that the order of elements in a collection is ignored."
  [opendata-results-map]
  (update-in opendata-results-map [:results :dataset]
             (fn [dataset]
               (into #{} (for [field dataset]
                           (util/map-values #(if (sequential? %) (set %) %) field))))))

(defn assert-collection-opendata-results-match
  "Returns true if the opendata results are for the expected items"
  [collections actual-result]
  (is (= (opendata-results-map->opendata-results-map-using-sets
           (collections->expected-opendata collections))
         (opendata-results-map->opendata-results-map-using-sets actual-result))))
