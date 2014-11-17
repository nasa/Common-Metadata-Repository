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

(def BUREAU_CODE
  "opendata bureauCode for NASA"
  "026:00")

(def PROGRAM_CODE
  "opendata programCode for NASA : Earth Science Research"
  "026:001")

(def PUBLISHER
  "opendata publisher string for NASA"
  "National Aeronautics and Space Administration")

(def LANGUAGE_CODE
  "opendata language code for NASA data"
  "en-US")

(def ACCESS_LEVEL
  "public")

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :opendata]
  [concept-type query]
  ;; TODO add spatial, etc.
  ["entry-title"
   "summary"
   "science-keywords-flat"
   "update-time"
   "insert-time"
   "concept-id"
   "short-name"
   "project-sn"
   "opendata-format"
   "related-urls"
   "start-date"
   "end-date"
   "ords-info"
   "ords"
   "contact-email"
   "contact-name"
   ;; needed for acl enforcment
   "access-value"
   "provider-id"
   ])

(defmethod elastic-results/elastic-result->query-result-item :opendata
  [_ _ elastic-result]
  (let [{concept-id :_id
         {[short-name] :short-name
          [summary] :summary
          [update-time] :update-time
          [insert-time] :insert-time
          [provider-id] :provider-id
          [project-sn] :project-sn
          [access-value] :access-value
          [science-keywords-flat] :science-keywords-flat
          [opendata-format] :opendata-format
          [access-url] :access-url
          related-urls :related-urls
          [entry-title] :entry-title
          ords-info :ords-info
          ords :ords
          [contact-email] :contact-email
          [contact-name] :contact-name
          [start-date] :start-date
          [end-date] :end-date} :fields} elastic-result
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))]
    {:id concept-id
     :title entry-title
     :short-name short-name
     :summary summary
     :update-time update-time
     :insert-time insert-time
     :concept-type :collection
     :access-url (ru/related-urls->opendata-access-url related-urls)
     :related-urls related-urls
     :project-sn project-sn
     :shapes (srl/ords-info->shapes ords-info ords)
     :contact-email contact-email
     :contact-name contact-name
     :start-date start-date
     :end-date end-date
     :provider-id provider-id
     :access-value access-value ;; needed for acl enforcment
     :keywords science-keywords-flat
     :entry-title entry-title}))

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
  "Creates the distribution field for the collection with the given related-urls."
  [related-urls]
  (let [online-access-urls (ru/downloadable-urls related-urls)]
    (map (fn [url]
           (let [mime-type (or (:mime-type url)
                               "application/octet-stream")]
             {:accessURL (:url url)
              :format mime-type}))
         online-access-urls)))

(defn landing-page
  "Creates the landingPage field for the collection with the given related-urls."
  [related-urls]
  (some (fn [related-url]
          (let [{:keys [url type]} related-url]
            (when (= "VIEW PROJECT HOME PAGE" type)
              url)))
        related-urls))

(defn result->opendata
  "Converts a search result item to opendata."
  [context concept-type pretty? item]
  (let [{:keys [id summary short-name project-sn update-time insert-time provider-id access-value
                keywords entry-title opendata-format start-date end-date
                related-urls contact-name contact-email shapes]} item
        related-urls (map #(json/decode % true) related-urls)]
    (util/remove-nil-keys {:title entry-title
                           :description summary
                           :keyword (not-empty keywords)
                           :modified update-time
                           :publisher PUBLISHER
                           :contactPoint contact-name
                           :mbox contact-email
                           :identifier id
                           :accessLevel ACCESS_LEVEL
                           :bureauCode [BUREAU_CODE]
                           :programCode [PROGRAM_CODE]
                           :accessURL (:url (first (ru/downloadable-urls related-urls)))
                           :format opendata-format
                           :spatial (spatial shapes pretty?)
                           :temporal (temporal start-date end-date)
                           :theme (not-empty (str/join "," project-sn))
                           :distribution (seq (distribution related-urls))
                           :landingPage (landing-page related-urls)
                           :language  [LANGUAGE_CODE]
                           :references (not-empty (map :url related-urls))
                           :issued insert-time})))

(defn results->opendata
  "Convert search results to opendata."
  [context concept-type pretty? results]
  (let [{:keys [items]} results]
    (map (partial result->opendata context concept-type pretty?) items)))

(defmethod qs/search-results->response :opendata
  [context query results]
  (let [{:keys [concept-type pretty? result-features]} query
        response-results (results->opendata
                           context concept-type pretty? results)]
    (json/generate-string response-results {:pretty pretty?})))