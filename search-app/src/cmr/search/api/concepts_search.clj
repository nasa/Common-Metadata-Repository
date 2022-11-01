(ns cmr.search.api.concepts-search
  "Defines the API for search-by-concept in the CMR."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [cmr.common-app.api.launchpad-token-validation :refer [get-token-type]]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.config :as common-app-config]
   [cmr.common-app.services.search :as search]
   [cmr.common.cache :as cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.generics :as common-generic]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as svc-errors]
   [cmr.common.util :as util]
   [cmr.search.api.core :as core-api]
   [cmr.search.services.parameters.legacy-parameters :as lp]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.services.result-format-helper :as rfh]
   [cmr.search.validators.all-granule-validation :as all-gran-validation]
   [compojure.core :refer :all]
   [inflections.core :as inf]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants and Utility Functions
(defconfig allow-all-granule-params-flag
  "Flag that indicates if we allow all granule queries."
  {:default true :type Boolean})

(defconfig allow-all-gran-header
  "This is the header that allows operators to run all granule queries when
   allow-all-granule-params-flag is set to false."
  {:default "Must be changed"})

(def supported-provider-holdings-mime-types
  "The mime types supported by search."
  #{mt/any
    mt/xml
    mt/json
    mt/csv})

(defn- concept-type-path-w-extension->concept-type
  "Parses the concept type and extension (\"granules.echo10\") into the concept type"
  [concept-type-w-extension]
  (let [ies (-> #"^(.+)ies(?:\..+)?"
                (re-matches concept-type-w-extension)
                second)]
    (if (some? ies)
      (keyword (str ies "y"))
      (-> #"^(.+)s(?:\..+)?"
          (re-matches concept-type-w-extension)
          second
          keyword))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(defn- validate-search-after-value
  "Validate the search-after value is in the form of a JSON array; otherwise throw 400 error"
  [search-after]
  (try
    (seq (json/parse-string search-after))
    (catch Exception e
      (error (format "search-after header value is invalid, error: %s" (.getMessage e)))
      (svc-errors/throw-service-error
       :bad-request
       "search-after header value is invalid, must be in the form of a JSON array."))))

(defn- validate-search-after-params
  "Validate search-after and params, throws service error if failed."
  [context params]
  (let [{:keys [scroll-id search-after]} context
        {:keys [page-num page_num offset scroll]} params]
    (when search-after
      (when scroll-id
        (svc-errors/throw-service-error :bad-request "scroll_id is not allowed with search-after"))
      (when page-num
        (svc-errors/throw-service-error :bad-request "page-num is not allowed with search-after"))
      (when page_num
        (svc-errors/throw-service-error :bad-request "page_num is not allowed with search-after"))
      (when offset
        (svc-errors/throw-service-error :bad-request "offset is not allowed with search-after"))
      (when scroll
        (svc-errors/throw-service-error :bad-request "scroll is not allowed with search-after")))))

(defn- validate-stac-params
  "Validate stac params, throws service error if failed."
  [context concept-type headers params]
  (when (= :stac (:result-format params))
    (when (not= :granule concept-type)
      (svc-errors/throw-service-error
       :bad-request "STAC result format is only supported for granule searches"))

    (let [content-type-header (get headers (string/lower-case common-routes/CONTENT_TYPE_HEADER))
          {:keys [scroll-id search-after query-string]} context
          {:keys [offset scroll]} params]
      (when-not (:collection-concept-id (util/map-keys->kebab-case params))
        (svc-errors/throw-service-error
         :bad-request "collection_concept_id is required for searching in STAC result format"))
      (when scroll
        (svc-errors/throw-service-error
         :bad-request "scroll is not allowed with STAC result format"))
      (when offset
        (svc-errors/throw-service-error
         :bad-request "offset is not allowed with STAC result format"))
      (when scroll-id
        (svc-errors/throw-service-error
         :bad-request "CMR-Scroll-Id header is not allowed with STAC result format"))
      (when search-after
        (svc-errors/throw-service-error
         :bad-request "CMR-Search-After header is not allowed with STAC result format")))))

(defn- find-concepts-by-json-query
  "Invokes query service to parse the JSON query, find results and return
  the response."
  [ctx path-w-extension params headers json-query]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        params (core-api/process-params concept-type params path-w-extension headers mt/xml)
        _ (when (= :stac (:result-format params))
            (svc-errors/throw-service-error
             :bad-request "search by JSON query is not allowed with STAC result format"))
        _ (validate-search-after-params ctx params)
        search-after (get headers (string/lower-case common-routes/SEARCH_AFTER_HEADER))
        log-message (format "Searching for %ss from client %s in format %s with JSON %s and query parameters %s"
                            (name concept-type) (:client-id ctx)
                            (rfh/printable-result-format (:result-format params)) json-query params)
        _ (info (if search-after
                  (format "%s, search-after: %s." log-message search-after)
                  (format "%s." log-message)))
        results (query-svc/find-concepts-by-json-query ctx concept-type params json-query)]
    (core-api/search-response ctx results)))

(defconfig block-queries
  "Indicates whether we are going to block a specific excessive query."
  {:type Boolean
   :default true})

(defn- block-excessive-queries
  "Temporary solution to prevent a specific query from overloading the CMR search resources."
  [ctx concept-type result-format params]
  (when (and (block-queries)
             (= concept-type :granule)
             (= :json result-format)
             (= "MCD43A4" (:short_name params))
             (contains? params ""))
    (warn (format "Blocking %s query from client %s in format %s with params %s."
                  (name concept-type)
                  (:client-id ctx)
                  (rfh/printable-result-format result-format)
                  (pr-str params)))
    (svc-errors/throw-service-error
     :too-many-requests
     (str "Excessive query rate. Please contact "
          (common-app-config/cmr-support-email) "."))))

(defn- reject-all-granule-query?
  "Return true if the all granule query will be rejected."
  [headers]
  (and (false? (allow-all-granule-params-flag))
       (or (not (some? (get headers "client-id")))
           (not (= "true" (get headers (string/lower-case (allow-all-gran-header))))))))

(defn- handle-all-granule-params
  "Throws error if all granule params needs to be rejected."
  [headers]
  (let [err-msg (str "The CMR does not allow querying across granules in all "
                     "collections. To help optimize your search, you should limit your "
                     "query using conditions that identify one or more collections, "
                     "such as provider, provider_id, concept_id, collection_concept_id, "
                     "short_name, version or entry_title. "
                     "For any questions please contact "
                     (common-app-config/cmr-support-email) ".")]
    (when (reject-all-granule-query? headers)
      (svc-errors/throw-service-error :bad-request err-msg))))

(defn- illegal-concept-id-in-granule-query?
  "Check to see if any concept ids are not starting with G and C."
  [concept-id-param]
  (when (some? concept-id-param)
    (let [concept-id-param-list (if (sequential? concept-id-param)
                                  (remove #(= "" %) concept-id-param)
                                  [concept-id-param])]
      (some #(not (re-find #"^[GC]" %)) concept-id-param-list))))

(defn empty-string-values?
  "This function is used by remove-map-keys function.
   The keys will be removed if their values are empty strings, or if it's sequential and
   only contain empty strings.
   Note:  When you pass a parameter without = sign, it is considered a nil value and get
   filtered out before getting to the params; with the = sign, no value, it becomes empty
   string."
  [v]
  (if (sequential? v)
    (empty? (remove #(= "" %) v))
    (= "" v)))

(defn- all-granule-params?
  "Returns true if it's a all granule query params.
   Note: parameters with scroll-id don't need to be checked because it inherits the search
   parameters from the original query, which would have been handled already."
  [scroll-id coll-constraints]
  (and (not (some? scroll-id))
       (empty? coll-constraints)))

(defn- handle-granule-search-params
  "Check the params when it is a granule search query."
  [headers concept-type params scroll-id]
  (when (= :granule concept-type)
    ;; Check to see if any concept-id(s) are not starting with C or G. If so, bad request.
    ;; otherwise, check to see if it's all-granule-params?, if so, handle it.
    (let [params (->> params
                      util/map-keys->kebab-case
                      lp/replace-parameter-aliases
                      (util/remove-map-keys empty-string-values?))
          constraints (select-keys params all-gran-validation/granule-limiting-search-fields)
          concept-id-param (:concept-id constraints)
          illegal-concept-id-msg (str "Invalid concept_id [" concept-id-param
                                      "]. For granule queries concept_id must be"
                                      " either a granule or collection concept ID.")]
      (if (illegal-concept-id-in-granule-query? concept-id-param)
        (svc-errors/throw-service-error :bad-request illegal-concept-id-msg)
        (when (all-granule-params? scroll-id constraints)
          (handle-all-granule-params headers))))))

(defn- find-concepts-by-parameters
  "Invokes query service to parse the parameters query, find results, and
  return the response"
  [ctx path-w-extension params headers body]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        short-scroll-id (get headers (string/lower-case common-routes/SCROLL_ID_HEADER))
        scroll-id-and-search-params (core-api/get-scroll-id-and-search-params-from-cache ctx short-scroll-id)
        scroll-id (:scroll-id scroll-id-and-search-params)
        cached-search-params (:search-params scroll-id-and-search-params)
        search-after (get headers (string/lower-case common-routes/SEARCH_AFTER_HEADER))
        ctx (assoc ctx :query-string body :scroll-id scroll-id :query-params params)
        params (core-api/process-params concept-type params path-w-extension headers mt/xml)
        result-format (:result-format params)
        _ (block-excessive-queries ctx concept-type result-format params)
        _ (validate-search-after-params ctx params)
        _ (validate-stac-params ctx concept-type headers params)
        log-message (format "Searching for %ss from client %s in format %s with params %s"
                            (name concept-type) (:client-id ctx)
                            (rfh/printable-result-format result-format) (pr-str params))
        _ (info (cond
                  short-scroll-id (format "%s, scroll-id: %s." log-message short-scroll-id)
                  search-after (format "%s, search-after: %s." log-message search-after)
                  :else (format "%s." log-message)))
        search-params (if cached-search-params
                        cached-search-params
                        (lp/process-legacy-psa params))
        _ (handle-granule-search-params headers concept-type search-params short-scroll-id)

        results (query-svc/find-concepts-by-parameters ctx concept-type search-params)]
    (if (:scroll-id results)
      (core-api/search-response ctx results search-params)
      (core-api/search-response ctx results))))

(defn- find-concepts
  "Invokes query service to find results and returns the response.

  This function supports several cases for obtaining concept data:
  * By JSON query
  * By parameter string and URL parameters
  * Collections from Granules - due to the fact that ES doesn't suport joins
    in the way that we need, we have to make two queries here to support CMR
    Harvesting. This can later be generalized easily, should the need arise."
  [ctx path-w-extension params headers body]
  (let [content-type-header (get headers (string/lower-case common-routes/CONTENT_TYPE_HEADER))
        search-after (get headers (string/lower-case common-routes/SEARCH_AFTER_HEADER))
        _ (validate-search-after-value search-after)
        ctx (assoc ctx :search-after (json/decode search-after))]
    (cond
      (= mt/json content-type-header)
      (find-concepts-by-json-query ctx path-w-extension params headers body)

      (or (nil? content-type-header)
          (= mt/form-url-encoded content-type-header)
          (re-find (re-pattern mt/multi-part-form) content-type-header))
      (find-concepts-by-parameters ctx path-w-extension params headers body)

      :else
      {:status 415
       :headers {common-routes/CORS_ORIGIN_HEADER "*"}
       :body (str "Unsupported content type [" content-type-header "]")})))

(defn- get-granules-timeline
  "Retrieves a timeline of granules within each collection found."
  [ctx path-w-extension params headers query-string]
  (let [params (core-api/process-params :granule params path-w-extension headers mt/json)
        _ (info (format "Getting granule timeline from client %s, token_type %s with params %s."
                        (:client-id ctx) (get-token-type (:token ctx)) (pr-str params)))
        search-params (lp/process-legacy-psa params)]
    (core-api/search-response ctx (query-svc/get-granule-timeline ctx search-params))))

(defn- find-concepts-by-aql
  "Invokes query service to parse the AQL query, find results and returns the response"
  [ctx path-w-extension params headers aql]
  (let [params (core-api/process-params nil params path-w-extension headers mt/xml)
        _ (when (= :stac (:result-format params))
            (svc-errors/throw-service-error
             :bad-request "search by JSON query is not allowed with STAC result format"))
        _ (info (format "Searching for concepts from client %s in format %s with AQL: %s and query parameters %s."
                        (:client-id ctx) (rfh/printable-result-format (:result-format params)) aql params))
        results (query-svc/find-concepts-by-aql ctx params aql)]
    (core-api/search-response ctx results)))

(defn- find-tiles
  "Retrieves all the tiles which intersect the input geometry"
  [ctx params]
  (core-api/search-response ctx {:results (query-svc/find-tiles-by-geometry ctx params)
                                 :result-format mt/json}))

(defn- find-data-json
  "Retrieve all public collections with gov.nasa.eosdis tag as opendata."
  [ctx]
  (core-api/search-response ctx {:results (query-svc/get-data-json-collections ctx)
                                 :result-format mt/json}))

(defn- get-deleted-collections
  "Invokes query service to search for collections that are deleted and returns the response"
  [ctx path-w-extension params headers]
  (let [params (core-api/process-params :collection params path-w-extension headers mt/xml)]
    (info (format "Searching for deleted collections from client %s in format %s with params %s."
                  (:client-id ctx) (rfh/printable-result-format (:result-format params))
                  (pr-str params)))
    (core-api/search-response ctx (query-svc/get-deleted-collections ctx params))))

(defn- get-deleted-granules
  "Invokes query service to search for granules that are deleted and returns the response"
  [ctx path-w-extension params headers]
  (let [params (core-api/process-params nil params path-w-extension headers mt/xml)]
    (info (format "Searching for deleted granules from client %s in format %s with params %s."
                  (:client-id ctx) (rfh/printable-result-format (:result-format params))
                  (pr-str params)))
    (core-api/search-response ctx (query-svc/get-deleted-granules ctx params))))

(defn- validate-content-type
  "Validate the given headers has the expected content type."
  [headers expected-content-type]
  (let [content-type-header (get headers (string/lower-case common-routes/CONTENT_TYPE_HEADER))]
    (when-not (= expected-content-type content-type-header)
      (svc-errors/throw-service-error
       :invalid-content-type (str "Unsupported content type [" content-type-header "]")))))

(defn- remove-scroll-results-from-cache
  "Removes the first page of results from a scroll session (along with the original query
  execution time) from the cache using the scroll-id as a key."
  [context scroll-id]
  (when scroll-id
    ;; clear the cache entry
    (-> context
        (cache/context->cache search/scroll-first-page-cache-key)
        (cache/set-value scroll-id nil))))

(defn- clear-scroll
  "Invokes query service to clear the scroll context for the given scroll id.
   The concept type of the request must be JSON."
  [context headers body]
  (validate-content-type headers mt/json)
  (if-let [short-scroll-id (-> body
                               (json/parse-string true)
                               :scroll_id)]
    (do
      (info (format "Clear scroll: %s" short-scroll-id))
      ;; if the short scroll id is valid, retrieve the real scroll id
      (if-let [scroll-id (->> short-scroll-id
                              (core-api/get-scroll-id-and-search-params-from-cache context)
                              :scroll-id)]
        ;; clear the scroll session for the scroll id and the first page of results from the cache
        (do
          (query-svc/clear-scroll context scroll-id)
          (remove-scroll-results-from-cache context scroll-id))
        (svc-errors/throw-service-error
         :invalid-data (format "scroll_id [%s] not found." short-scroll-id))))
    (svc-errors/throw-service-error
     :invalid-data "scroll_id must be provided."))
  ;; no errors, return 204
  {:status 204})

(def routes-regex
  "Appends the generic concepts dynamically loaded to the non-generic concepts to match possible routes general form: (?:(?:granules)|...(?:data-quality-summaries)"
  (re-pattern (str "(?:(?:granules)|(?:collections)|(?:variables)|(?:subscriptions)|(?:tools)|(?:services)|" 
                   common-generic/plural-generic-concept-types-reg-ex
                   ")(?:\\..+)?")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def search-routes
  "Routes for /search/granules, /search/collections, etc."
  (context ["/:path-w-extension" :path-w-extension routes-regex] [path-w-extension]
    (OPTIONS "/" req (common-routes/options-response))
    (GET "/"
      {params :params headers :headers ctx :request-context query-string :query-string}
      (find-concepts (merge ctx {:method :get}) path-w-extension params headers query-string))
    ;; Find concepts - form encoded or JSON
    (POST "/"
      {params :params headers :headers ctx :request-context body :body-copy}
      (find-concepts (merge ctx {:method :post}) path-w-extension params headers body))))

(def granule-timeline-routes
  "Routes for /search/granules/timeline."
  (context ["/granules/:path-w-extension" :path-w-extension #"(?:timeline)(?:\..+)?"] [path-w-extension]
    (OPTIONS "/" req (common-routes/options-response))
    (GET "/"
      {params :params headers :headers ctx :request-context query-string :query-string}
      (get-granules-timeline ctx path-w-extension params headers query-string))
    (POST "/" {params :params headers :headers ctx :request-context body :body-copy}
      (get-granules-timeline ctx path-w-extension params headers body))))

(def find-deleted-concepts-routes
  "Routes for finding deleted granules and collections."
  (routes
   (context ["/:path-w-extension" :path-w-extension #"(?:deleted-collections)(?:\..+)?"] [path-w-extension]
     (OPTIONS "/" req (common-routes/options-response))
     (GET "/"
       {params :params headers :headers ctx :request-context}
       (get-deleted-collections ctx path-w-extension params headers)))

   (context ["/:path-w-extension" :path-w-extension #"(?:deleted-granules)(?:\..+)?"] [path-w-extension]
     (OPTIONS "/" req (common-routes/options-response))
     (GET "/"
       {params :params headers :headers ctx :request-context}
       (get-deleted-granules ctx path-w-extension params headers)))))

(def aql-search-routes
  "Routes for finding concepts using the ECHO Alternative Query Language (AQL)."
  (context ["/concepts/:path-w-extension" :path-w-extension #"(?:search)(?:\..+)?"] [path-w-extension]
    (OPTIONS "/" req (common-routes/options-response))
    (POST "/"
      {params :params headers :headers ctx :request-context body :body-copy}
      (find-concepts-by-aql ctx path-w-extension params headers body))))

(def tiles-routes
  "Routes for /search/tiles."
  (GET "/tiles"
    {params :params ctx :request-context}
    (find-tiles ctx params)))

(def clear-scroll-routes
  "Routes for /search/clear-scroll."
  (POST "/clear-scroll"
    {ctx :request-context headers :headers body :body-copy}
    (clear-scroll ctx headers body)))

(def data-json-routes
  "Route for data.json response. Socrata does not support harvesting from
   data.json endpoints that do not explicitly end in /data.json. This is needed
   to harvest CMR opendata responses on data.nasa.gov. This endpoint returns
   collections with the gov.nasa.eosdis tag as opendata."
  (GET "/socrata/data.json"
    {ctx :request-context}
    (find-data-json ctx)))
