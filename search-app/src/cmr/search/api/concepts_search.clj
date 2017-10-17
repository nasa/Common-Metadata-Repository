(ns cmr.search.api.concepts-search
  "Defines the API for search-by-concept in the CMR."
  (:require
   [clojure.string :as string]
   ;; XXX REMOVE the next require once the service and associations work is complete
   [cmr-edsc-stubs.core :as stubs]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.services.search :as search]
   [cmr.common.cache :as cache]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as svc-errors]
   [cmr.search.api.core :as core-api]
   [cmr.search.services.parameters.legacy-parameters :as lp]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.services.result-format-helper :as rfh]
   [compojure.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants and Utility Functions

(def supported-provider-holdings-mime-types
  "The mime types supported by search."
  #{mt/any
    mt/xml
    mt/json
    mt/csv})

(defn- concept-type-path-w-extension->concept-type
  "Parses the concept type and extension (\"granules.echo10\") into the concept type"
  [concept-type-w-extension]
  (-> #"^([^s]+)s(?:\..+)?"
      (re-matches concept-type-w-extension)
      second
      keyword))

(defn- get-scroll-id-from-cache
  "Returns the full ES scroll-id from the cache using the short scroll-id as a key. Throws a
  service error :not-found if the key does not exist in the cache."
  [context short-scroll-id]
  (when short-scroll-id
    (if-let [scroll-id (-> context
                           (cache/context->cache search/scroll-id-cache-key)
                           (cache/get-value short-scroll-id))]
      scroll-id
      (svc-errors/throw-service-error
       :not-found
       (format "Scroll session [%s] does not exist" short-scroll-id)))))

(defn- process-params
  "Processes the parameters by removing unecessary keys and adding other keys like result format."
  [concept-type params ^String path-w-extension headers default-mime-type]
  (let [result-format (core-api/get-search-results-format concept-type path-w-extension headers default-mime-type)
        ;; Continue to treat the search extension "umm-json" as the legacy umm json response for now
        ;; to avoid breaking clients
        result-format (if (.endsWith path-w-extension ".umm-json")
                        :legacy-umm-json
                        result-format)
        ;; For search results umm-json is an alias of umm-json-results since we can't actually return
        ;; a set of search results that would match the UMM-C JSON schema
        result-format (if (= :umm-json (:format result-format))
                        (assoc result-format :format :umm-json-results)
                        result-format)]
    (-> params
        (dissoc :path-w-extension :token)
        (assoc :result-format result-format))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(defn- find-concepts-by-json-query
  "Invokes query service to parse the JSON query, find results and return
  the response."
  [ctx path-w-extension params headers json-query]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        params (process-params concept-type params path-w-extension headers mt/xml)
        _ (info (format "Searching for %ss from client %s in format %s with JSON %s and query parameters %s."
                        (name concept-type) (:client-id ctx)
                        (rfh/printable-result-format (:result-format params)) json-query params))
        results (query-svc/find-concepts-by-json-query ctx concept-type params json-query)]
    (core-api/search-response ctx results)))

(defn- find-concepts-by-parameters
  "Invokes query service to parse the parameters query, find results, and
  return the response"
  [ctx path-w-extension params headers body]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        short-scroll-id (get headers (string/lower-case common-routes/SCROLL_ID_HEADER))
        scroll-id (get-scroll-id-from-cache ctx short-scroll-id)
        ctx (assoc ctx :query-string body :scroll-id scroll-id)
        params (process-params concept-type params path-w-extension headers mt/xml)
        result-format (:result-format params)
        _ (info (format "Searching for %ss from client %s in format %s with params %s."
                        (name concept-type) (:client-id ctx)
                        (rfh/printable-result-format result-format) (pr-str params)))
        search-params (lp/process-legacy-psa params)
        results (query-svc/find-concepts-by-parameters ctx concept-type search-params)]
    (core-api/search-response ctx results)))

(defn- find-concepts
  "Invokes query service to find results and returns the response.

  This function supports several cases for obtaining concept data:
  * By JSON query
  * By parameter string and URL parameters
  * Collections from Granules - due to the fact that ES doesn't suport joins
    in the way that we need, we have to make two queries here to support CMR
    Harvesting. This can later be generalized easily, should the need arise."
  [ctx path-w-extension params headers body]
  (let [content-type-header (get headers (string/lower-case common-routes/CONTENT_TYPE_HEADER))]
    (cond
      (= mt/json content-type-header)
      (find-concepts-by-json-query ctx path-w-extension params headers body)

      (or (nil? content-type-header) (= mt/form-url-encoded content-type-header))
      (find-concepts-by-parameters ctx path-w-extension params headers body)

      :else
      {:status 415
       :headers {common-routes/CORS_ORIGIN_HEADER "*"}
       :body (str "Unsupported content type ["
                  (get headers (string/lower-case common-routes/CONTENT_TYPE_HEADER)) "]")})))

(defn- get-granules-timeline
  "Retrieves a timeline of granules within each collection found."
  [ctx path-w-extension params headers query-string]
  (let [params (process-params :granule params path-w-extension headers mt/json)
        _ (info (format "Getting granule timeline from client %s with params %s."
                        (:client-id ctx) (pr-str params)))
        search-params (lp/process-legacy-psa params)
        results (query-svc/get-granule-timeline ctx search-params)]
    {:status 200
     :headers {common-routes/CORS_ORIGIN_HEADER "*"}
     :body results}))

(defn- find-concepts-by-aql
  "Invokes query service to parse the AQL query, find results and returns the response"
  [ctx path-w-extension params headers aql]
  (let [params (process-params nil params path-w-extension headers mt/xml)
        _ (info (format "Searching for concepts from client %s in format %s with AQL: %s and query parameters %s."
                        (:client-id ctx) (rfh/printable-result-format (:result-format params)) aql params))
        results (query-svc/find-concepts-by-aql ctx params aql)]
    (core-api/search-response ctx results)))

(defn- find-tiles
  "Retrieves all the tiles which intersect the input geometry"
  [ctx params]
  (let [results (query-svc/find-tiles-by-geometry ctx params)]
    {:status 200
     :headers {common-routes/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)}
     :body results}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(defn- get-deleted-collections
  "Invokes query service to search for collections that are deleted and returns the response"
  [ctx path-w-extension params headers]
  (let [params (process-params :collection params path-w-extension headers mt/xml)]
    (info (format "Searching for deleted collections from client %s in format %s with params %s."
                  (:client-id ctx) (rfh/printable-result-format (:result-format params))
                  (pr-str params)))
    (core-api/search-response ctx (query-svc/get-deleted-collections ctx params))))

(defn- get-deleted-granules
  "Invokes query service to search for granules that are deleted and returns the response"
  [ctx path-w-extension params headers]
  (let [params (process-params nil params path-w-extension headers mt/xml)]
    (info (format "Searching for deleted granules from client %s in format %s with params %s."
                  (:client-id ctx) (rfh/printable-result-format (:result-format params))
                  (pr-str params)))
    (core-api/search-response ctx (query-svc/get-deleted-granules ctx params))))

(def search-routes
  (context ["/:path-w-extension" :path-w-extension #"(?:(?:granules)|(?:collections)|(?:variables)|(?:services))(?:\..+)?"] [path-w-extension]
    (OPTIONS "/" req common-routes/options-response)
    (GET "/"
      {params :params headers :headers ctx :request-context query-string :query-string}
      ;; XXX REMOVE this check and the stubs once the service and
      ;;     the associations work is complete
      (if (headers "cmr-prototype-umm")
        (stubs/handle-prototype-request
         path-w-extension params headers query-string)
        (find-concepts ctx path-w-extension params headers query-string)))
    ;; Find concepts - form encoded or JSON
    (POST "/"
      {params :params headers :headers ctx :request-context body :body-copy}
      (find-concepts ctx path-w-extension params headers body))))

(def granule-timeline-routes
  (context ["/granules/:path-w-extension" :path-w-extension #"(?:timeline)(?:\..+)?"] [path-w-extension]
    (OPTIONS "/" req common-routes/options-response)
    (GET "/"
      {params :params headers :headers ctx :request-context query-string :query-string}
      (get-granules-timeline ctx path-w-extension params headers query-string))
    (POST "/" {params :params headers :headers ctx :request-context body :body-copy}
      (get-granules-timeline ctx path-w-extension params headers body))))

(def deleted-routes
  (routes
    (context ["/:path-w-extension" :path-w-extension #"(?:deleted-collections)(?:\..+)?"] [path-w-extension]
      (OPTIONS "/" req common-routes/options-response)
      (GET "/"
        {params :params headers :headers ctx :request-context}
        (get-deleted-collections ctx path-w-extension params headers)))

    (context ["/:path-w-extension" :path-w-extension #"(?:deleted-granules)(?:\..+)?"] [path-w-extension]
      (OPTIONS "/" req common-routes/options-response)
      (GET "/"
        {params :params headers :headers ctx :request-context}
        (get-deleted-granules ctx path-w-extension params headers)))))

(def aql-search-routes
  (context ["/concepts/:path-w-extension" :path-w-extension #"(?:search)(?:\..+)?"] [path-w-extension]
    (OPTIONS "/" req common-routes/options-response)
    (POST "/"
      {params :params headers :headers ctx :request-context body :body-copy}
      (find-concepts-by-aql ctx path-w-extension params headers body))))

(def tiles-routes
  (GET "/tiles"
    {params :params ctx :request-context}
    (find-tiles ctx params)))
