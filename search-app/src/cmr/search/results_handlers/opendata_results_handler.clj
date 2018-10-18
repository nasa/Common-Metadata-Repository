(ns cmr.search.results-handlers.opendata-results-handler
  "Handles the opendata results format and related functions"
  (:require
   [cheshire.core :as json]
   [clj-time.core :as time]
   [clj-time.format :as f]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.results-model :as r]
   [cmr.common.services.errors :as svc-errors]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.opendata-spatial-results-handler :as opendata-spatial]
   [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.search.services.url-helper :as url]
   [cmr.spatial.serialize :as srl]
   [cmr.umm-spec.date-util :as umm-spec-date-util]
   [cmr.umm-spec.util :as umm-spec-util]
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

(defmethod gcrf/query-results->concept-ids :opendata
  [results]
  (svc-errors/throw-service-error
    :bad-request
    "Collections search in opendata format is not supported with include_granule_counts option"))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :opendata]
  [concept-type query]
  (let [opendata-fields ["entry-title"
                         "summary"
                         "science-keywords-flat"
                         "update-time"
                         "insert-time"
                         "concept-id"
                         "short-name"
                         "project-sn"
                         "related-urls"
                         "publication-references"
                         "start-date"
                         "end-date"
                         "revision-date"
                         "granule-start-date"
                         "granule-end-date"
                         "ords-info"
                         "ords"
                         "personnel"
                         "archive-center"]]
    (distinct (concat opendata-fields acl-rhh/collection-elastic-fields))))

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

(defmethod elastic-results/elastic-result->query-result-item [:collection :opendata]
  [_ _ elastic-result]
  (let [{concept-id :_id
         {[short-name] :short-name
          [summary] :summary
          [update-time] :update-time
          [insert-time] :insert-time
          [provider-id] :provider-id
          project-sn :project-sn
          science-keywords-flat :science-keywords-flat
          [opendata-format] :opendata-format
          related-urls :related-urls
          publication-references :publication-references
          [entry-title] :entry-title
          ords-info :ords-info
          ords :ords
          [personnel] :personnel
          [start-date] :start-date
          [end-date] :end-date
          [revision-date] :revision-date
          [granule-start-date] :granule-start-date
          [granule-end-date] :granule-end-date
          [archive-center] :archive-center} :fields} elastic-result
        personnel (json/decode personnel true)
        related-urls  (map #(json/decode % true) related-urls)
        start-date (when start-date (string/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (string/replace (str end-date) #"\+0000" "Z"))
        revision-date (when revision-date (string/replace (str revision-date) #"\+0000" "Z"))
        granule-start-date (when granule-start-date
                             (string/replace (str granule-start-date) #"\+0000" ".000Z"))
        granule-end-date (when granule-end-date
                           (string/replace (str granule-end-date) #"\+0000" ".000Z"))]
    (merge {:id concept-id
            :title entry-title
            :short-name short-name
            :summary summary
            :update-time update-time
            :insert-time insert-time
            :concept-type :collection
            :related-urls related-urls
            :publication-references publication-references
            :project-sn project-sn
            :shapes (srl/ords-info->shapes ords-info ords)
            :personnel personnel
            :start-date start-date
            :end-date end-date
            :revision-date revision-date
            :granule-start-date granule-start-date
            :granule-end-date granule-end-date
            :provider-id provider-id
            :science-keywords-flat science-keywords-flat
            :entry-title entry-title
            :archive-center archive-center}
           (acl-rhh/parse-elastic-item :collection elastic-result))))

(defn generate-end-date
  "Format today's date if the given end-date is nil"
  [end-date]
  (if end-date
   end-date
   (f/unparse (f/formatters :date-time-no-ms) (clj-time.core/today-at 0 0))))

(defn temporal
  "Get the temporal field from the start-date and end-date"
  ;; NOTE: Eventually this should handle peridoc-date-times as well, but opendata only allows one
  ;; entry whereas UMM-C allows multiple periodic-date-time entries. This will have to wait until
  ;; a decision is made about how to resolve multiple periodic-date-time entries.
  [start-date end-date]
  (when start-date
    (let [end-date (generate-end-date end-date)]
      (str start-date "/" end-date))))

(defn spatial
  "Get the spatial field from the spatial elements of the collection."
  [spatial]
  (opendata-spatial/shapes->json spatial))

(defn related-url->distribution
  "Returns a distribution open data field for the provided related URL.
  See https://project-open-data.cio.gov/v1.1/schema/#dataset-distribution-fields."
  [related-url]
  (let [{:keys [description url]} related-url]
    (util/remove-nil-keys {:accessURL (ru/related-url->encoded-url url)
                           :description description})))

(defn landing-page
  "Creates the landingPage field for the collection with the given related-urls.
  Returns umm-spec-lib default if none is present."
  [related-urls]
  (if (empty? related-urls)
    umm-spec-util/not-provided-url
    (some (fn [related-url]
            (let [{:keys [url type]} related-url]
              (when (or (= "PROJECT HOME PAGE" type) ; UMM-C terminology
                        (= "VIEW PROJECT HOME PAGE" type)) ; ECHO-10 terminology
                url)))
          related-urls)))

(defn publisher
  "Creates the publisher field for the collection based on the archive-center.  Note for the
  USGS_EROS provider the hierarchy is different than for the other providers."
  [provider-id archive-center]
  (let [hierarchy (if (= provider-id "USGS_EROS")
                    USGS_EROS_PUBLISHER_HIERARCHY
                    NASA_PUBLISHER_HIERARCHY)]
    {:name (or archive-center umm-spec-util/not-provided)
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

(defn- get-issued-modified-time
  "Get collection's issued/modified time. Parameter time could be either
  the collection's insert-time or update-time. Parameter gran-time could be either
  granule-start-date or granule-end-date.
  when insert-time/update-time is nil or default value, get issued/modified time
  from collection's earliest granule's start-date, and latest granule's end-date."
  [time gran-time]
  (if (and (not-empty time) (not= time (str umm-spec-date-util/parsed-default-date)))
    time
    gran-time))

(defn- result->opendata
  "Converts a search result item to opendata."
  [context concept-type item]
  (let [{:keys [id summary short-name project-sn update-time insert-time provider-id
                science-keywords-flat entry-title opendata-format start-date end-date
                related-urls publication-references personnel shapes archive-center
                revision-date granule-start-date granule-end-date]} item
        issued-time (get-issued-modified-time insert-time granule-start-date)
        ;; if issued-time is default date, set it to nil.
        issued-time (when-not (= issued-time (str umm-spec-date-util/parsed-default-date))
                      issued-time)
        modified-time (get-issued-modified-time update-time granule-end-date)]
    ;; All fields are required unless otherwise noted
    (util/remove-nil-keys {:title (or entry-title umm-spec-util/not-provided)
                           :description (not-empty summary)
                           :keyword (keywords science-keywords-flat)
                           :modified (or modified-time revision-date)
                           :publisher (publisher provider-id archive-center)
                           :contactPoint (contact-point personnel)
                           :identifier id
                           :accessLevel ACCESS_LEVEL
                           :bureauCode [BUREAU_CODE]
                           :programCode [PROGRAM_CODE]
                           :spatial (spatial shapes) ;; required if applicable
                           :temporal (temporal start-date end-date) ;; required if applicable
                           :theme (theme project-sn) ;; not required
                           :distribution (not-empty (map related-url->distribution related-urls))
                           :landingPage (landing-page related-urls)
                           :language  [LANGUAGE_CODE]
                           :references (not-empty
                                        (map ru/related-url->encoded-url publication-references))
                           :issued (not-empty issued-time)})))

(defn- results->opendata
  "Convert search results to opendata."
  [context concept-type results]
  (let [{:keys [items]} results]
    {:conformsTo OPENDATA_SCHEMA
     :dataset (map (partial result->opendata context concept-type) items)}))

(defmethod qs/search-results->response [:collection :opendata]
  [context query results]
  (let [{:keys [concept-type result-features]} query
        response-results (results->opendata
                           context concept-type results)]
    (json/generate-string response-results)))
