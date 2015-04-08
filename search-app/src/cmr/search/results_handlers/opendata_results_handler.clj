(ns cmr.search.results-handlers.opendata-results-handler
  "Handles the opendata results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.search.results-handlers.opendata-spatial-results-handler :as opendata-spatial]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.set :as set]
            [clj-time.core :as time]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [cmr.search.models.results :as r]
            [cmr.spatial.serialize :as srl]
            [cmr.search.services.url-helper :as url]
            [cmr.umm.related-url-helper :as ru]))

(def OPENDATA_SCHEMA
  "Location of the the opendata schema to which the results conform"
  "https://project-open-data.cio.gov/v1.1/schema")

(def BUREAU_CODE
  "opendata bureauCode for NASA"
  "026:00")

(def PROGRAM_CODE
  "opendata programCode for NASA : Earth Science Research"
  "026:001")

(def NASA_PUBLISHER_HIERARCHY
  "opendata publisher hierarchy for NASA providers"
  ;; Improve readability by ensuring :name always appears before nested :subOrganizationOf map
  (sorted-map :name "National Aeronautics and Space Administration",
              :subOrganizationOf {:name "U.S. Government"}))

(def USGS_EROS_PUBLISHER_HIERARCHY
  "opendata publisher hierarchy for the USGS_EROS provider"
  ;; Improve readability by ensuring :name always appears before nested :subOrganizationOf map
  (sorted-map :name "U.S. Geological Survey",
              :subOrganizationOf {:name "U.S. Department of the Interior",
                                  :subOrganizationOf {:name "U.S. Government"}}))

(def LANGUAGE_CODE
  "opendata language code for NASA data"
  "en-US")

(def ACCESS_LEVEL
  "public")

(def DEFAULT_CONTACT_EMAIL
  "support@earthdata.nasa.gov")

(def DEFAULT_CONTACT_NAME
  "undefined")

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :opendata]
  [concept-type query]
  ["entry-title"
   "summary"
   "science-keywords-flat"
   "update-time"
   "insert-time"
   "concept-id"
   "short-name"
   "project-sn"
   "related-urls"
   "start-date"
   "end-date"
   "ords-info"
   "ords"
   "personnel"
   "archive-center"
   ;; needed for acl enforcment
   "access-value"
   "provider-id"
   ])

(defn personnel->contact-name
  "Returns a contact name from the personnel record or the default if one
  is not available."
  [personnel]
  (or (let [{:keys [first-name last-name]} personnel]
        (if first-name
          (str first-name " " last-name)
          last-name))
      DEFAULT_CONTACT_NAME))

(defn personnel->contact-email
  "Returns a contact email from the personnel record or the default if one
  is not available."
  [personnel]
  (or (:value (first (filter #(= "email" (:type %))
                             (:contacts personnel))))
      DEFAULT_CONTACT_EMAIL))

(defn contact-point
  "Creates the contactPoint field including the name and email address"
  [personnel]
  {:fn (personnel->contact-name personnel)
   :hasEmail (str "mailto:" (personnel->contact-email personnel))})

(defmethod elastic-results/elastic-result->query-result-item :opendata
  [_ _ elastic-result]
  (let [{concept-id :_id
         {[short-name] :short-name
          [summary] :summary
          [update-time] :update-time
          [insert-time] :insert-time
          [provider-id] :provider-id
          project-sn :project-sn
          [access-value] :access-value
          science-keywords-flat :science-keywords-flat
          [opendata-format] :opendata-format
          related-urls :related-urls
          [entry-title] :entry-title
          ords-info :ords-info
          ords :ords
          [personnel] :personnel
          [start-date] :start-date
          [end-date] :end-date
          [archive-center] :archive-center} :fields} elastic-result
        personnel (json/decode personnel true)
        related-urls  (map #(json/decode % true) related-urls)
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))]
    {:id concept-id
     :title entry-title
     :short-name short-name
     :summary summary
     :update-time update-time
     :insert-time insert-time
     :concept-type :collection
     :related-urls related-urls
     :project-sn project-sn
     :shapes (srl/ords-info->shapes ords-info ords)
     :personnel personnel
     :start-date start-date
     :end-date end-date
     :provider-id provider-id
     :access-value access-value ;; needed for acl enforcment
     :science-keywords-flat science-keywords-flat
     :entry-title entry-title
     :archive-center archive-center}))

(defn temporal
  "Get the temporal field from the start-date and end-date"
  ;; NOTE: Eventually this should handle peridoc-date-times as well, but opendata only allows one
  ;; entry whereas UMM-C allows multiple periodic-date-time entries. This will have to wait until
  ;; a decision is made about how to resolve multiple periodic-date-time entries.
  [start-date end-date]
  (not-empty (if (and start-date end-date)
               (str start-date "/" end-date)
               start-date)))

(defn spatial
  "Get the spatial field from the spatial elements of the collection."
  [spatial pretty?]
  (opendata-spatial/shapes->json spatial pretty?))

(defn distribution
  "Creates the distribution field for the collection with the given related-urls.  If a URL is
  downloadable the URL will be marked as a :downloadURL otherwise it will be an :accessURL."
  [related-urls]
  (not-empty (map (fn [url]
                    (let [mime-type (or (:mime-type url)
                                        "application/octet-stream")
                          url-type (if (ru/downloadable-url? url)
                                     :downloadURL
                                     :accessURL)]
                      {url-type (:url url)
                       :mediaType mime-type}))
                  related-urls)))

(defn landing-page
  "Creates the landingPage field for the collection with the given related-urls."
  [related-urls]
  (some (fn [related-url]
          (let [{:keys [url type]} related-url]
            (when (= "VIEW PROJECT HOME PAGE" type)
              url)))
        related-urls))

(defn publisher
  "Creates the publisher field for the collection based on the archive-center.  Note for the
  USGS_EROS provider the hierarchy is different than for the other providers."
  [provider-id archive-center]
  (let [hierarchy (if (= provider-id "USGS_EROS")
                    USGS_EROS_PUBLISHER_HIERARCHY
                    NASA_PUBLISHER_HIERARCHY)]
    {:name archive-center
     :subOrganizationOf hierarchy}))

(defn keywords
  "Create the keyword field for the collection based on the science keywords for the collection.
  Takes the science keywords as a flat list rather than hierarchical."
  [science-keywords-flat]
  (conj science-keywords-flat "NGDA" "National Geospatial Data Asset"))

(defn theme
  "Create the theme field for the collection based on the project short-names for the collection."
  [project-short-names]
  (conj project-short-names "geospatial"))

(defn- result->opendata
  "Converts a search result item to opendata."
  [context concept-type pretty? item]
  (let [{:keys [id summary short-name project-sn update-time insert-time provider-id
                science-keywords-flat entry-title opendata-format start-date end-date
                related-urls personnel shapes archive-center]} item]
    (util/remove-nil-keys {:title entry-title
                           :description (not-empty summary)
                           :keyword (keywords science-keywords-flat)
                           :modified (not-empty update-time)
                           :publisher (publisher provider-id archive-center)
                           :contactPoint (contact-point personnel)
                           :identifier id
                           :accessLevel ACCESS_LEVEL
                           :bureauCode [BUREAU_CODE]
                           :programCode [PROGRAM_CODE]
                           :spatial (spatial shapes pretty?)
                           :temporal (temporal start-date end-date)
                           :theme (theme project-sn)
                           :distribution (distribution related-urls)
                           :landingPage (landing-page related-urls)
                           :language  [LANGUAGE_CODE]
                           :references (not-empty (map :url related-urls))
                           :issued (not-empty insert-time)})))

(defn- results->opendata
  "Convert search results to opendata."
  [context concept-type pretty? results]
  (let [{:keys [items]} results]
    {:conformsTo OPENDATA_SCHEMA
     :dataset (map (partial result->opendata context concept-type pretty?) items)}))

(defmethod qs/search-results->response :opendata
  [context query results]
  (let [{:keys [concept-type pretty? result-features]} query
        response-results (results->opendata
                           context concept-type pretty? results)]
    (json/generate-string response-results {:pretty pretty?})))
