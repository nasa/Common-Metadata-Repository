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
          [access-url] :access-url
          related-urls :related-urls
          [entry-title] :entry-title
          ords-info :ords-info
          ords :ords
          [personnel] :personnel
          [start-date] :start-date
          [end-date] :end-date} :fields} elastic-result
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
    (not-empty (map (fn [url]
                      (let [mime-type (or (:mime-type url)
                                          "application/octet-stream")]
                        {:accessURL (:url url)
                         :format mime-type}))
                    online-access-urls))))

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
                related-urls personnel shapes]} item
        access-url (first (ru/downloadable-urls related-urls))
        distribution (distribution related-urls)]
    (util/remove-nil-keys {:title entry-title
                           :description (not-empty summary)
                           :keyword (not-empty keywords)
                           :modified (not-empty update-time)
                           :publisher PUBLISHER
                           :contactPoint (personnel->contact-name personnel)
                           :mbox (personnel->contact-email personnel)
                           :identifier id
                           :accessLevel ACCESS_LEVEL
                           :bureauCode [BUREAU_CODE]
                           :programCode [PROGRAM_CODE]
                           :accessURL (:accessURL (first distribution))
                           :format (:format (first distribution))
                           :spatial (spatial shapes pretty?)
                           :temporal (temporal start-date end-date)
                           :theme (not-empty (str/join "," project-sn))
                           :distribution distribution
                           :landingPage (landing-page related-urls)
                           :language  [LANGUAGE_CODE]
                           :references (not-empty (map :url related-urls))
                           :issued (not-empty insert-time)})))

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