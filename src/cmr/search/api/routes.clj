(ns cmr.search.api.routes
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
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

            ;; Result handlers
            ;; required here to avoid circular dependency in query service
            [cmr.search.results-handlers.csv-results-handler]
            [cmr.search.results-handlers.atom-results-handler]
            [cmr.search.results-handlers.atom-json-results-handler]
            [cmr.search.results-handlers.reference-results-handler]
            [cmr.search.results-handlers.metadata-results-handler]
            [cmr.search.results-handlers.all-collections-results-handler]))

(def extension->mime-type
  "A map of URL file extensions to the mime type they represent."
  {"json" "application/json"
   "xml" "application/xml"
   "echo10" "application/echo10+xml"
   "iso_prototype" "application/iso_prototype+xml"
   "smap_iso" "application/iso:smap+xml"
   "iso19115" "application/iso19115+xml"
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
    "text/csv"})

(def supported-provider-holdings-mime-types
  "The mime types supported by search."
  #{"*/*"
    "application/xml"
    "application/json"})

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
  ([path-w-extension headers]
   (get-search-results-format path-w-extension headers supported-mime-types))
  ([path-w-extension headers valid-mime-types]
   (let [ext-mime-type (path-w-extension->mime-type path-w-extension)
         mime-type (or ext-mime-type (get headers "accept"))]
     (mt/validate-request-mime-type mime-type valid-mime-types)
     ;; set the default format to xml
     (mt/mime-type->format mime-type :xml))))

(defn- get-token
  "Returns the token the user passed in the headers or parameters"
  [params headers]
  (or (:token params)
      (get headers "echo-token")))

(defn process-params
  "Processes the parameters by removing unecessary keys and adding other keys like result format."
  [params path-w-extension headers]
  (-> params
      (dissoc :path-w-extension)
      (dissoc :token)
      (assoc :result-format (get-search-results-format path-w-extension headers))))

(defn- find-concepts
  "Invokes query service to find results and returns the response"
  [context path-w-extension params headers query-string]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        context (-> context
                    (assoc :query-string query-string)
                    (assoc :token (get-token params headers)))
        params (process-params params path-w-extension headers)
        _ (info (format "Searching for %ss in format %s with params %s."
                        (name concept-type) (:result-format params) (pr-str params)))
        search-params (lp/process-legacy-psa params query-string)
        results (query-svc/find-concepts-by-parameters context concept-type search-params)]
    {:status 200
     :headers {"Content-Type" (str (mt/format->mime-type (:result-format params)) "; charset=utf-8")}
     :body results}))

(defn- find-concepts-by-aql
  "Invokes query service to parse the AQL query, find results and returns the response"
  [context path-w-extension params headers aql]
  (let [context (assoc context :token (get-token params headers))
        params (process-params params path-w-extension headers)
        _ (info (format "Searching for concepts in format %s with AQL: %s."
                        (:result-format params) aql))
        results (query-svc/find-concepts-by-aql context params aql)]
    {:status 200
     :headers {"Content-Type" (str (mt/format->mime-type (:result-format params)) "; charset=utf-8")}
     :body results}))

(defn- find-concept-by-cmr-concept-id
  "Invokes query service to find concept metadata by cmr concept id and returns the response"
  [context path-w-extension params headers]
  (let [context (assoc context :token (get-token params headers))
        result-format (get-search-results-format path-w-extension headers)
        concept-id (path-w-extension->concept-id path-w-extension)
        _ (info (format "Search for concept with cmr-concept-id [%s]" concept-id))
        concept (query-svc/find-concept-by-id context result-format concept-id)]
    {:status 200
     :headers {"Content-Type" "application/xml; charset=utf-8"}
     :body (:metadata concept)}))

(defn- get-provider-holdings
  "Invokes query service to retrieve provider holdings and returns the response"
  [context path-w-extension params headers]
  (let [context (assoc context :token (get-token params headers))
        params (process-params params path-w-extension headers)
        _ (info (format "Searching for provider holdings in format %s with params %s." (:result-format params) (pr-str params)))
        provider-holdings (query-svc/get-provider-holdings context params)]
    {:status 200
     :headers {"Content-Type" (str (mt/format->mime-type (:result-format params)) "; charset=utf-8")}
     :body provider-holdings}))

(def concept-type-w-extension-regex
  "A regular expression that matches URLs including the concept type (pluralized) along with a file
  extension."
  #"(?:(?:granules)|(?:collections))(?:\..+)?")

(def concept-id-w-extension-regex
  "A regular expression matching URLs including a concept id along with a file extension"
  #"(?:[A-Z][0-9]+-[0-9A-Z_]+)(?:\..+)?")

(def provider-holdings-w-extension-regex
  "A regular expression that matches URLs including the provider holdings and a file extension."
  #"(?:provider_holdings)(?:\..+)?")

(def search-w-extension-regex
  "A regular expression that matches URLs including the search and a file extension."
  #"(?:search)(?:\..+)?")

(defn- build-routes [system]
  (routes
    (context (get-in system [:search-public-conf :relative-root-url]) []

      ;; Retrieve by cmr concept id
      (context ["/concepts/:path-w-extension" :path-w-extension concept-id-w-extension-regex] [path-w-extension]
        (GET "/" {params :params headers :headers context :request-context}
          (find-concept-by-cmr-concept-id context path-w-extension params headers)))

      ;; Find concepts
      (context ["/:path-w-extension" :path-w-extension concept-type-w-extension-regex] [path-w-extension]
        (GET "/" {params :params headers :headers context :request-context query-string :query-string}
          (find-concepts context path-w-extension params headers query-string))
        (POST "/" {params :params headers :headers context :request-context body :body-copy}
          (find-concepts context path-w-extension params headers body)))

      ;; AQL search
      (context ["/concepts/:path-w-extension" :path-w-extension search-w-extension-regex] [path-w-extension]
        (POST "/" {params :params headers :headers context :request-context body :body-copy}
          (find-concepts-by-aql context path-w-extension params headers body)))

      ;; Provider holdings
      (context ["/:path-w-extension" :path-w-extension provider-holdings-w-extension-regex] [path-w-extension]
        (GET "/" {params :params headers :headers context :request-context}
          (get-provider-holdings context path-w-extension params headers)))

      ;; reset operation available just for development purposes
      ;; clear the cache for search app
      (POST "/reset" {:keys [request-context]}
        (query-svc/reset request-context)
        {:status 200}))
    (route/not-found "Not Found")))

;; Copies the body into a new attributed called :body-copy so that after a post of form content type
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
      errors/exception-handler
      handler/site
      copy-of-body-handler
      ring-json/wrap-json-body
      ring-json/wrap-json-response))
