(ns cmr.search.api.routes
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.services.errors :as svc-errors]
            [cmr.common.mime-types :as mt]
            [cmr.search.services.query-service :as query-svc]
            [cmr.system-trace.http :as http-trace]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.acl.core :as acl]

            ;; Result handlers
            ;; required here to avoid circular dependency in query service
            [cmr.search.results-handlers.csv-results-handler]
            [cmr.search.results-handlers.atom-results-handler]
            [cmr.search.results-handlers.atom-json-results-handler]
            [cmr.search.results-handlers.reference-results-handler]
            [cmr.search.results-handlers.metadata-results-handler]
            [cmr.search.results-handlers.query-specified-results-handler]
            [cmr.search.results-handlers.timeline-results-handler]

            ;; ACL support. Required here to avoid circular dependencies
            [cmr.search.services.acls.collection-acls]
            [cmr.search.services.acls.granule-acls]))

(def TOKEN_HEADER "echo-token")
(def CONTENT_TYPE_HEADER "Content-Type")
(def HITS_HEADER "CMR-Hits")
(def TOOK_HEADER "CMR-Took")
(def CMR_GRANULE_COUNT_HEADER "CMR-Granule-Hits")
(def CMR_COLLECTION_COUNT_HEADER "CMR-Collection-Hits")

(def extension->mime-type
  "A map of URL file extensions to the mime type they represent."
  {"json" "application/json"
   "xml" "application/xml"
   "echo10" "application/echo10+xml"
   "iso" "application/iso19115+xml"
   "iso19115" "application/iso19115+xml"
   "iso_smap" "application/iso:smap+xml"
   "dif" "application/dif+xml"
   "csv" "text/csv"
   "atom" "application/atom+xml"})

(def supported-mime-types
  "The mime types supported by search."
  #{"*/*"
    "application/xml"
    "application/json"
    "application/echo10+xml"
    "application/dif+xml"
    "application/atom+xml"
    "application/iso19115+xml"
    "application/iso:smap+xml"
    "text/csv"})

(def supported-provider-holdings-mime-types
  "The mime types supported by search."
  #{"*/*"
    "application/xml"
    "application/json"})

(def supported-concept-id-retrieval-mime-types
  #{"*/*"
    "application/xml" ; allows retrieving native format
    "application/echo10+xml"
    "application/iso19115+xml"
    "application/iso:smap+xml"
    "application/dif+xml"})

(defn- search-response-headers
  "Generate headers for search response."
  [content-type results]
  {CONTENT_TYPE_HEADER (str content-type "; charset=utf-8")
   HITS_HEADER (str (:hits results))
   TOOK_HEADER (str (:took results))})

(defn- concept-type-path-w-extension->concept-type
  "Parses the concept type and extension (\"granules.echo10\") into the concept type"
  [concept-type-w-extension]
  (-> #"^([^s]+)s(?:\..+)?"
      (re-matches concept-type-w-extension)
      second
      keyword))

(defn- path-w-extension->mime-type
  "Parses the search path with extension and returns the requested mime-type or nil if no extension
  was passed."
  [search-path-w-extension]
  (when-let [extension (second (re-matches #"[^.]+(?:\.(.+))$" search-path-w-extension))]
    (or (extension->mime-type extension)
        (svc-errors/throw-service-error
          :bad-request (format "The URL extension [%s] is not supported." extension)))))

(defn- path-w-extension->concept-id
  "Parses the path-w-extension to remove the concept id from the beginning"
  [path-w-extension]
  (second (re-matches #"([^\.]+)(?:\..+)?" path-w-extension)))



(defn- get-search-results-format
  "Returns the requested search results format parsed from headers or from the URL extension"
  ([path-w-extension headers default-mime-type]
   (get-search-results-format path-w-extension headers supported-mime-types default-mime-type))
  ([path-w-extension headers valid-mime-types default-mime-type]
   (let [ext-mime-type (path-w-extension->mime-type path-w-extension)
         mime-type (or ext-mime-type
                       (mt/mime-type-from-headers headers valid-mime-types)
                       default-mime-type)]
     (mt/validate-request-mime-type mime-type valid-mime-types)
     ;; set the default format to xml
     (mt/mime-type->format mime-type default-mime-type))))

(defn process-params
  "Processes the parameters by removing unecessary keys and adding other keys like result format."
  [params path-w-extension headers default-mime-type]
  (-> params
      (dissoc :path-w-extension)
      (dissoc :token)
      (assoc :result-format (get-search-results-format path-w-extension headers default-mime-type))))

(defn- search-response
  "Generate the response map for finding concepts by params or AQL."
  [params results]
  {:status 200
   :headers (search-response-headers (mt/format->mime-type (:result-format params)) results)
   :body (:results results)})

(defn- find-concepts
  "Invokes query service to find results and returns the response"
  [context path-w-extension params headers query-string]
  (let [content-type-header (get headers (str/lower-case CONTENT_TYPE_HEADER))]
    (if (or (nil? content-type-header)
            (= "application/x-www-form-urlencoded" content-type-header))
      (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
            context (-> context
                        (acl/add-authentication-to-context params headers)
                        (assoc :query-string query-string))
            params (process-params params path-w-extension headers "application/xml")
            result-format (:result-format params)
            _ (when (= :iso-smap result-format)
                (svc-errors/throw-service-error
                  :bad-request "Searching in iso_smap format is not supported."))
            _ (info (format "Searching for %ss from client %s in format %s with params %s."
                            (name concept-type) (:client-id context) result-format
                            (pr-str params)))

            ;; :iso is aliases of :iso19115
            params (if (= :iso result-format)
                     (assoc params :result-format :iso19115) params)
            search-params (lp/process-legacy-psa params query-string)
            results (query-svc/find-concepts-by-parameters context concept-type search-params)]
        (search-response params results))
      {:status 415
       :body (str "Unsupported content type ["
                  (get headers (str/lower-case CONTENT_TYPE_HEADER)) "]")})))

(defn- get-granules-timeline
  "Retrieves a timeline of granules within each collection found."
  [context path-w-extension params headers query-string]
  (let [context (acl/add-authentication-to-context context params headers)
        params (process-params params path-w-extension headers "application/json")
        _ (info (format "Getting granule timeline from client %s with params %s."
                        (:client-id context) (pr-str params)))
        search-params (lp/process-legacy-psa params query-string)
        results (query-svc/get-granule-timeline context search-params)]
    (r/response results)))

(defn- find-concepts-by-aql
  "Invokes query service to parse the AQL query, find results and returns the response"
  [context path-w-extension params headers aql]
  (let [context (acl/add-authentication-to-context context params headers)
        params (process-params params path-w-extension headers "application/xml")
        _ (info (format "Searching for concepts from client %s in format %s with AQL: %s and query parameters %s."
                        (:client-id context) (:result-format params) aql params))
        results (query-svc/find-concepts-by-aql context params aql)]
    (search-response params results)))

(defn- find-concept-by-cmr-concept-id
  "Invokes query service to find concept metadata by cmr concept id and returns the response"
  [context path-w-extension params headers]
  (let [context (acl/add-authentication-to-context context params headers)
        result-format (get-search-results-format path-w-extension headers
                                                 supported-concept-id-retrieval-mime-types
                                                 "application/xml")
        concept-id (path-w-extension->concept-id path-w-extension)
        _ (info (format "Search for concept with cmr-concept-id [%s]" concept-id))
        concept (query-svc/find-concept-by-id context result-format concept-id)]
    {:status 200
     :headers {CONTENT_TYPE_HEADER (str (:format concept) "; charset=utf-8")}
     :body (:metadata concept)}))

(defn- get-provider-holdings
  "Invokes query service to retrieve provider holdings and returns the response"
  [context path-w-extension params headers]
  (let [context (acl/add-authentication-to-context context params headers)
        params (process-params params path-w-extension headers "application/json")
        _ (info (format "Searching for provider holdings from client %s in format %s with params %s."
                        (:client-id context) (:result-format params) (pr-str params)))
        [provider-holdings provider-holdings-formatted]
        (query-svc/get-provider-holdings context params)
        collection-count (count provider-holdings)
        granule-count (reduce + (map :granule-count provider-holdings))]
    {:status 200
     :headers {CONTENT_TYPE_HEADER (str (mt/format->mime-type (:result-format params)) "; charset=utf-8")
               CMR_COLLECTION_COUNT_HEADER (str collection-count)
               CMR_GRANULE_COUNT_HEADER (str granule-count)}
     :body provider-holdings-formatted}))

(defn- build-routes [system]
  (routes
    (context (get-in system [:search-public-conf :relative-root-url]) []

      ;; CMR Welcome Page
      (GET "/" []
        {:status 200
         :body (slurp (io/resource "public/index.html"))})

      (GET "/site/:resource" [resource]
        {:status 200
         :body (slurp (io/resource (str "public/site/" resource)))})

      ;; Retrieve by cmr concept id -
      (context ["/concepts/:path-w-extension" :path-w-extension #"(?:[A-Z][0-9]+-[0-9A-Z_]+)(?:\..+)?"] [path-w-extension]
        (GET "/" {params :params headers :headers context :request-context}
          (find-concept-by-cmr-concept-id context path-w-extension params headers)))

      ;; Find concepts
      (context ["/:path-w-extension" :path-w-extension #"(?:(?:granules)|(?:collections))(?:\..+)?"] [path-w-extension]
        (GET "/" {params :params headers :headers context :request-context query-string :query-string}
          (find-concepts context path-w-extension params headers query-string))
        ;; Find concepts - form encoded
        (POST "/" {params :params headers :headers context :request-context body :body-copy}
          (find-concepts context path-w-extension params headers body)))

      ;; Granule timeline
      (context ["/granules/:path-w-extension" :path-w-extension #"(?:timeline)(?:\..+)?"] [path-w-extension]
        (GET "/" {params :params headers :headers context :request-context query-string :query-string}
          (get-granules-timeline context path-w-extension params headers query-string)))

      ;; AQL search - xml
      (context ["/concepts/:path-w-extension" :path-w-extension #"(?:search)(?:\..+)?"] [path-w-extension]
        (POST "/" {params :params headers :headers context :request-context body :body-copy}
          (find-concepts-by-aql context path-w-extension params headers body)))

      ;; Provider holdings
      (context ["/:path-w-extension" :path-w-extension #"(?:provider_holdings)(?:\..+)?"] [path-w-extension]
        (GET "/" {params :params headers :headers context :request-context}
          (get-provider-holdings context path-w-extension params headers)))

      ;; Resets the application back to it's initial state.
       (POST "/reset" {:keys [request-context params headers]}
        (query-svc/clear-cache (acl/add-authentication-to-context request-context params headers))
        {:status 200})

      ;; Clears the cache.
      (POST "/clear-cache" {:keys [request-context params headers]}
        (query-svc/clear-cache (acl/add-authentication-to-context request-context params headers))
        {:status 200}))
    (route/not-found "Not Found")))

;; Copies the body into a new attribute called :body-copy so that after a post of form content type
;; the original body can still be read. The default ring params reads the body and parses it and we
;; don't have access to it.
(defn copy-of-body-handler
  [f]
  (fn [request]
    (let [^String body (slurp (:body request))]
      (f (assoc request
                :body-copy body
                :body (java.io.ByteArrayInputStream. (.getBytes body)))))))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      handler/site
      copy-of-body-handler
      errors/exception-handler
      ring-json/wrap-json-response))
