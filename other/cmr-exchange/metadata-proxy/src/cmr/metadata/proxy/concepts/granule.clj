(ns cmr.metadata.proxy.concepts.granule
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.util :as util]
   [cmr.exchange.query.util :as query-util]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.metadata.proxy.components.config :as config]
   [cmr.metadata.proxy.const :as const]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

(defn- build-include
  [gran-ids]
  (string/join
   "&"
   (conj
    (map #(str (codec/url-encode "concept_id[]")
               "="
               %)
         gran-ids))))

(defn- build-exclude
  [gran-ids]
  (string/join
   "&"
   (conj
    (map #(str (codec/url-encode "exclude[echo_granule_id][]")
               "="
               %)
         gran-ids))))

(defn build-query
  "Build the query string for querying granles, bassed upon the options
  passed in the parameters."
  [component params]
  (let [coll-id (:collection-id params)
        gran-ids (util/remove-empty (:granules params))
        exclude? (:exclude-granules params)
        {:keys [bounding-box temporal page-num page-size]} params]
    (str "collection_concept_id=" coll-id
         (when (seq gran-ids)
           (str "&"
                (if exclude?
                  (build-exclude gran-ids)
                  (build-include gran-ids))))
         (when (seq bounding-box)
           (str "&bounding_box="
                (query-util/seq->str bounding-box)))
         (when (seq temporal)
           (str "&"
                (query-util/temporal-seq->cmr-query temporal)))
         (when (seq page-num)
           (str "&page-num=" page-num))
         (when (seq page-size)
           (str "&page-size=" page-size)))))

(defn async-get-metadata
  "Given a data structure with :collection-id, :granules, and :exclude-granules
  keys, get the metadata for the desired granules.

  Which granule metadata is returned depends upon the values of :granules and
  :exclude-granules"
  [component search-endpoint user-token params sa-header]
  (let [url (str search-endpoint "/granules")
        payload (build-query component params)]
    (log/debug "Granules query CMR URL:" url)
    (log/debug "Granules query CMR payload:" payload)
    (request/async-post
     url
     (as-> {} request
       (request/add-token-header request user-token)
       (request/add-accept request "application/json")
       (request/add-form-ct request)
       (request/add-payload request payload)
       (if (some? sa-header)
         (request/add-search-after request sa-header)
         request)
       ((fn [x] (log/debug "Client request options:" x) x) request))
     {}
     response/json-handler)))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error metadata-errors/granule-metadata)
        rslts)
      (do
        (log/trace "Got results from CMR granule search:"
                   (results/elided rslts))
        (log/trace "Remaining results:" (results/remaining-items rslts))
        (get-in rslts [:body :feed :entry])))))

(defn extract-body-data
  "Extracts the body of a granule response"
  [promise]
  (let [results @promise]
    (log/trace "Got headers from CMR granule:" results)
    (:body results)))

(defn extract-header-data
  "Extracts the headers from a granule response"
  [promise]
  (let [results @promise]
    (log/trace "Got headers from CMR granule:" results)
    (:headers results)))

(defn get-metadata
  [component search-endpoint user-token params sa-header]
  (let [promise (async-get-metadata component search-endpoint user-token params sa-header)]
    (extract-metadata promise)))

(defn match-datafile-link
  "The criteria defined in the prototype was to iterate through the links,
  only examining those links that were not 'inherited', and find the one
  whose :rel value matched a particular string.

  It is currently unclear what the best criteria for this decision is."
  [link-data]
  (log/trace "Link data:" link-data)
  (let [rel (:rel link-data)]
    (and (not (:inherited link-data))
         (= const/datafile-link-rel rel))))

(def opendap-lowercase
  "All lowercase OPeNDAP."
  "opendap")

(defn match-opendap-link
  "The matching performed is trying to find the case insensitive string opendap
  anywhere in the title for the URL. Until metadata is standardized for
  specifying OPeNDAP links (and all providers have updated their metadata we'll
  have to use this imprecise check)."
  [link-data]
  (log/trace "Link data:" link-data)
  (let [lower-case-title (some-> link-data :title string/lower-case)]
    (and (not (:inherited link-data))
         lower-case-title
         (string/includes? lower-case-title opendap-lowercase))))

(defn extract-granule-links
  "Returns an OPeNDAP link and a data download link from the granule metadata file.
  We return the first link of each type from the granule entry if there are
  multiple matches."
  [granule-entry]
  (log/trace "Granule entry: " granule-entry)
  (let [opendap-link (->> (:links granule-entry)
                          (filter match-opendap-link)
                          first)
        datafile-link (->> (:links granule-entry)
                           (filter match-datafile-link)
                           first)
        gran-id (:id granule-entry)]
    (if (or opendap-link datafile-link)
      {:granule-id gran-id
       :opendap-link opendap-link
       :datafile-link datafile-link}
      {:errors [metadata-errors/empty-granule-links
                (when gran-id
                  (format metadata-errors/problem-granules gran-id))]})))
