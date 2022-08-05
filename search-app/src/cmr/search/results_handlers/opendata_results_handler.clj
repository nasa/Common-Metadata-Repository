(ns cmr.search.results-handlers.opendata-results-handler
  "Handles the opendata results format and related functions"
  (:require
   [cheshire.core :as json]
   [clj-time.core :as time]
   [clj-time.format :as f]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [cmr.common-app.config :as common-app-config]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.results-model :as r]
   [cmr.common.doi :as doi]
   [cmr.common.services.errors :as svc-errors]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.opendata-spatial-results-handler :as opendata-spatial]
   [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.search.services.url-helper :as url]
   [cmr.spatial.serialize :as srl]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.date-util :as umm-spec-date-util]
   [cmr.umm-spec.related-url :as related-url]
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

(def USGS_PROVIDER
  "The USGS provider ID."
  "USGS_EROS")

(def NASA_PUBLISHER_HIERARCHY
  "opendata publisher hierarchy for NASA providers."
  ;; Improve readability by ensuring :name always appears before nested :subOrganizationOf map
  (sorted-map :name "National Aeronautics and Space Administration",
              :subOrganizationOf {:name "U.S. Government"}))

(def USGS_EROS_PUBLISHER_HIERARCHY
  "opendata publisher hierarchy for the USGS_EROS provider."
  ;; Improve readability by ensuring :name always appears before nested :subOrganizationOf map
  (sorted-map :name "U.S. Geological Survey",
              :subOrganizationOf {:name "U.S. Department of the Interior",
                                  :subOrganizationOf {:name "U.S. Government"}}))

(def NASA_PUBLICATION_NAME
  "Full NASA publication name."
  "National Aeronautics and Space Administration, U.S. Government")

(def USGS_PUBLICATION_NAME
  "Full USGS publication name."
  "U.S. Geological Survey, U.S. Department of the Interior, U.S. Government")

(def LANGUAGE_CODE
  "opendata language code for NASA data"
  "en-US")

(def ACCESS_LEVEL
  "public")

(def DEFAULT_CONTACT_EMAIL
  (common-app-config/cmr-support-email))

(def DEFAULT_CONTACT_NAME
  "undefined")

(defmethod gcrf/query-results->concept-ids :opendata
  [results]
  (svc-errors/throw-service-error
    :bad-request
    "Collections search in opendata format is not supported with include_granule_counts option"))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :opendata]
  [concept-type query]
  (let [opendata-fields ["archive-center"
                         "concept-id"
                         "doi"
                         "end-date"
                         "entry-title"
                         "granule-end-date"
                         "granule-start-date"
                         "insert-time"
                         "instruments"
                         "ords"
                         "ords-info"
                         "personnel"
                         "project-sn"
                         "publication-references"
                         "collection-citations"
                         "related-urls"
                         "revision-date"
                         "science-keywords-flat"
                         "short-name"
                         "start-date"
                         "summary"
                         "update-time"]]
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
         {short-name :short-name
          summary :summary
          update-time :update-time
          insert-time :insert-time
          provider-id :provider-id
          project-sn :project-sn
          science-keywords-flat :science-keywords-flat
          opendata-format :opendata-format
          related-urls :related-urls
          publication-references :publication-references
          collection-citations :collection-citations
          entry-title :entry-title
          ords-info :ords-info
          ords :ords
          instruments :instruments
          doi :doi
          personnel :personnel
          start-date :start-date
          end-date :end-date
          revision-date :revision-date
          granule-start-date :granule-start-date
          granule-end-date :granule-end-date
          archive-center :archive-center} :_source} elastic-result
        personnel (json/decode personnel true)
        related-urls  (map #(json/decode % true) related-urls)
        collection-citations (map #(json/decode % true) collection-citations)
        start-date (when start-date (string/replace (str start-date) #"\.000Z" "Z"))
        end-date (when end-date (string/replace (str end-date) #"\.000Z" "Z"))]
    (merge {:id concept-id
            :concept-id concept-id
            :title entry-title
            :short-name short-name
            :summary summary
            :update-time update-time
            :insert-time insert-time
            :concept-type :collection
            :related-urls related-urls
            :publication-references publication-references
            :collection-citations collection-citations
            :project-sn project-sn
            :shapes (srl/ords-info->shapes ords-info ords)
            :instruments instruments
            :doi doi
            :personnel personnel
            :start-date start-date
            :end-date end-date
            :revision-date revision-date
            :granule-start-date granule-start-date
            :granule-end-date granule-end-date
            :provider-id provider-id
            :science-keywords-flat science-keywords-flat
            :entry-title entry-title
            ;; opendata treats archive center as if it is a single value,
            ;; I don't have time to look into the exact business logic for doing this.
            ;; here I just take the first value out to keep the code functioning the same way.
            :archive-center (first archive-center)}
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
  (let [{:keys [description url get-data-mime-type url-content-type type sub-type]} related-url
        get-data-mime-type (or (util/nil-if-value umm-spec-util/not-provided get-data-mime-type)
                               (ru/infer-url-mime-type url))
        downloadable? (ru/downloadable-mime-type? get-data-mime-type)
        url-type (if downloadable? :downloadURL :accessURL)]
    (util/remove-nil-keys {url-type (ru/related-url->encoded-url url)
                           :mediaType (when downloadable? get-data-mime-type)
                           :title (related-url/related-url->title url-content-type type sub-type)
                           :description description})))

(defn landing-page
  "Creates the landingPage field for the collection either based on the DOI or using the CMR
  HTML collection page."
  [context item]
  (let [base-url (transmit-config/application-public-root-url context)]
    (doi/get-landing-page base-url item)))

(defn publisher
  "Creates the publisher field for the collection based on the archive-center.  Note for the
  USGS_EROS provider the hierarchy is different than for the other providers."
  [provider-id archive-center]
  (let [hierarchy (if (= provider-id USGS_PROVIDER)
                    USGS_EROS_PUBLISHER_HIERARCHY
                    NASA_PUBLISHER_HIERARCHY)]
    {:name (or archive-center umm-spec-util/not-provided)
     :subOrganizationOf hierarchy}))

(defn modis-aster?
  "Logic to test for collections that use the MODIS or ASTER instruments as
  these are the only ones that should be included in the data.gov output as
  defined by The Data Curation for Discovery (DCD) team at Marshall Space Flight
  Center (MSFC)"
  [metadata]
  (let [{:keys [short-name]} metadata]
    (or (= "MODIS" short-name) (= "ASTER" short-name))))

(defn keywords
  "Create the keyword field for the collection based on the science keywords for
  the collection, but only add these for records that match the correct
  conditions. Takes the science keywords as a flat list rather than hierarchical
  and the current item needed for finding conditions."
  [science-keywords-flat item]
  (if (some modis-aster? (:instruments item))
    (conj science-keywords-flat "NGDA" "National Geospatial Data Asset")
    science-keywords-flat))

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

(defn- title->citation-title
  "Format citation title and version."
  [title version]
  (when title
    (let [version-string (if version (str ". Version " version) "")]
      (str title version-string))))

(defn- name->citation-name
  "Format citation series-name, title, and version. Acceptable formats:
  series-name. Version version. title
  series-name. Version version
  series-name. title
  series-name
  title. Version version
  title"
  [series-name title version]
  (if (and series-name title)
    (str (title->citation-title series-name version) ". " title)
    (title->citation-title (or series-name title) version)))

(defn- publisher->citation-publisher
  "Format publisher for citation."
  [archive-center provider-id]
  (when archive-center
    (let [publication-name (if (= USGS_PROVIDER provider-id)
                             USGS_PUBLICATION_NAME
                             NASA_PUBLICATION_NAME)]
      (format "Archived by %s, %s" publication-name archive-center))))

(defn- doi->citation-doi
  "Format DOI for citation."
  [doi]
  (when doi
    (doi/doi->url doi)))

(defn- release-date->citation-release-date
  "Format release-date for citation."
  [release-date]
  (when release-date
    (first (string/split release-date #"T"))))

(def ^:private citation-order
  "Citation get functions in the order necessary to create the citation field."
  [:creator
   :editor
   #(release-date->citation-release-date (:release-date %)) ; The ReleaseDate
   #(name->citation-name (:series-name %) (:title %) (:version %)) ; The SeriesName, Title and Version
   :release-place
   :issue-identification
   #(publisher->citation-publisher (or (:publisher %) (:archive-center %)) (:provider-id %)) ; The Publisher
   #(doi->citation-doi (:doi %)) ; The DOI
   :online-resource
   :data-presentation-form
   :other-citation-details])

(defn- evaluate-citation
  "Evaluate citation against citation-order."
  [citation-information]
  (map #(% citation-information) citation-order))

(defn citation
  "Create the citation field for the opendata response. extra-fields contain
  information outside of CollectionCitations to create the full citation field.
  When OtherCitationDetails is the ONLY provided field then do not construct
  the citation and assume that contains the full citation."
  ([collection-citation]
   (citation collection-citation {}))
  ([collection-citation extra-fields]
   (if (= [:other-citation-details] (keys collection-citation))
     (:other-citation-details collection-citation)
     (when-let [citation-details (->> (merge collection-citation extra-fields)
                                      evaluate-citation
                                      (remove nil?)
                                      not-empty)]
       (str (string/join ". " citation-details) ".")))))

(defn- score-browse-image-related-url
  "Score the related-url based off number of browse-image fields."
  [related-url]
  (->> (select-keys related-url [:get-data-mime-type :description])
       vals
       (remove #(= umm-spec-util/not-provided %))
       count))

(defn get-best-browse-image-related-url
  "Get the related-url that is a browse image with the most graphic-preview fields."
  [related-urls]
  (->> (ru/browse-urls related-urls)
       (sort-by score-browse-image-related-url)
       last))

(defn graphic-preview-type
  "Nil if browse image mime type is Not provided."
  [browse-image-related-url]
  (util/nil-if-value umm-spec-util/not-provided (:get-data-mime-type browse-image-related-url)))

(defn- doi->distribution
  "Returns a distribution open data field for the provided DOI.
  See https://project-open-data.cio.gov/v1.1/schema/#dataset-distribution-fields."
  [doi]
  (when doi
    {:accessURL (ru/related-url->encoded-url
                 (str "https://scholar.google.com/scholar?q=" (or (second
                                                                   (re-find #"^doi:(.*)$" doi))
                                                                  doi)))
     :title "Google Scholar search results",
     :description "Search results for publications that cite this dataset by its DOI."}))

(defn- make-distributions
  "Using given related-urls and doi make list of distributions"
  [related-urls doi]
  (->> related-urls
       (map related-url->distribution)
       (into [(doi->distribution doi)])
       (remove nil?)
       not-empty))

(defn- result->opendata
  "Converts a search result item to opendata."
  [context concept-type item]
  (let [{:keys [id summary short-name project-sn update-time insert-time provider-id
                science-keywords-flat entry-title opendata-format start-date end-date
                related-urls publication-references personnel shapes archive-center
                revision-date granule-start-date granule-end-date collection-citations doi]} item
        issued-time (get-issued-modified-time insert-time granule-start-date)
        ;; if issued-time is default date, set it to nil.
        issued-time (when-not (= issued-time (str umm-spec-date-util/parsed-default-date))
                      issued-time)
        modified-time (get-issued-modified-time update-time granule-end-date)
        collection-citation (first collection-citations)
        browse-image-related-url (get-best-browse-image-related-url related-urls)]
    ;; All fields are required unless otherwise noted
    (util/remove-nil-keys {:title (or entry-title umm-spec-util/not-provided)
                           :description (not-empty summary)
                           :keyword (keywords science-keywords-flat item)
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
                           :graphic-preview-file (:url browse-image-related-url)
                           :graphic-preview-description (:description browse-image-related-url)
                           :graphic-preview-type (graphic-preview-type browse-image-related-url)
                           :landingPage (landing-page context item)
                           :distribution (make-distributions related-urls doi)
                           :language  [LANGUAGE_CODE]
                           :citation (citation collection-citation (select-keys item [:archive-center :doi :provider]))
                           :creator (:creator collection-citation)
                           :editor (:editor collection-citation)
                           :series-name (:series-name collection-citation)
                           :release-place (:release-place collection-citation)
                           :issue-identification (:issue-identification collection-citation)
                           :data-presentation-form (:data-presentation-form collection-citation)
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
