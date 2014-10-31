(ns cmr.search.results-handlers.opendata-results-handler
  "Handles the opendata results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.set :as set]
            [clj-time.core :as time]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [cmr.search.models.results :as r]
            [cmr.spatial.serialize :as srl]
            [cmr.search.services.url-helper :as url]))

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
   "access-url"
   "start-date"
   "end-date"
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
          [entry-title] :entry-title
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
     :opendata-format opendata-format
     :access-url access-url
     :project-sn project-sn
     :start-date start-date
     :end-date end-date
     :provider-id provider-id
     :access-value access-value ;; needed for acl enforcment
     :keywords science-keywords-flat
     :entry-title entry-title}))

(defn short-name->access-level
  "Return the access-level value based on the given short-name."
  [short-name]
  (if (and short-name (re-find #"AST" short-name))
    "restricted public"
    "public"))

(defn temporal
  "Get the temporal field from the start-date and end-date"
  ;; TODO Eventually this should handle peridoc-date-times as well, but opendata only allows one
  ;; entry whereas UMM-C allows multiple periodic-date-time entries. This will have to wait until
  ;; a decision is made about how to resolve multiple periodic-date-time entries.
  [start-date end-date]
  (not-empty (if (and start-date end-date)
               (str start-date "/" end-date)
               start-date)))

(defn result->opendata
  "Converts a search result item to opendata."
  [context concept-type item]
  (let [{:keys [id summary short-name project-sn update-time insert-time provider-id access-value
                keywords entry-title opendata-format access-url start-date end-date]} item]
    (util/remove-nil-keys {:title entry-title
                           :description summary
                           :keyword keywords
                           :modified update-time
                           :publisher PUBLISHER
                           ;; TODO :conctactPoint
                           ;; TODO :mbox
                           :identifier id
                           :accessLevel (short-name->access-level short-name)
                           :bureauCode BUREAU_CODE
                           :programCode PROGRAM_CODE
                           ;; TODO :accessLevelComment :access-constraints
                           :accessURL access-url
                           :format opendata-format
                           ;; TODO :spatial
                           :temporal (temporal start-date end-date)
                           :theme (not-empty (str/join "," project-sn))
                           ;; TODO :distribution
                           ;; TODO :accrualPeriodicity
                           ;; TODO :landingPage
                           :language  LANGUAGE_CODE
                           ;; TODO :references related-urls
                           :issued insert-time})))

(defn results->opendata
  "Convert search results to opendata."
  [context concept-type results]
  (let [{:keys [items]} results]
    (map (partial result->opendata context concept-type) items)))

(defmethod qs/search-results->response :opendata
  [context query results]
  (let [{:keys [concept-type pretty? echo-compatible? result-features]} query
        response-results (results->opendata
                           context concept-type results)]
    (json/generate-string response-results {:pretty pretty?})))