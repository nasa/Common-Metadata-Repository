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
            [cmr.search.api.atom-search-results]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.url-helper :as url]


            ;; Result handlers
            ;; required here to avoid circular dependency in query service
            [cmr.search.results-handlers.csv-results-handler]
            [cmr.search.results-handlers.atom-results-handler]
            [cmr.search.results-handlers.reference-results-handler]
            [cmr.search.results-handlers.metadata-results-handler]))

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

(defn- parse-concept-type-w-extension
  "Parses the concept type and extension (\"granules.echo10\") into a pair of concept type keyword
  and mime type"
  [concept-type-w-extension]
  (let [[_ concept-type extension]
        (filter
          identity
          (re-matches #"^(.+)s(?:\.(.+))?$" concept-type-w-extension))
        mime-type (extension->mime-type extension)]
    (when (and (nil? mime-type) (some? extension))
      (svc-errors/throw-service-error
        :bad-request (format "The URL extension [%s] is not supported." extension)))
    [(keyword concept-type)
     mime-type]))

(defn- get-search-results-format
  "Returns the requested search results format parsed from headers or from the URL extension"
  [ext-mime-type headers]
  (let [mime-type (or ext-mime-type (get headers "accept"))]
    (mt/validate-request-mime-type mime-type supported-mime-types)
    (mt/mime-type->format mime-type)))

(defn- find-concepts
  "Invokes query service to find results and returns the response"
  [context concept-type-w-extension params headers query-string]
  (let [[concept-type ext-mime-type] (parse-concept-type-w-extension concept-type-w-extension)
        params (dissoc params :concept-type-w-extension)
        result-format (get-search-results-format ext-mime-type headers)
        params (assoc params :result-format result-format)
        _ (info (format "Searching for %ss in format %s with params %s." (name concept-type) result-format (pr-str params)))

        ;; TODO - We shouldn't be stuffing this info in the context.
        context (assoc context :concept-type-w-extension concept-type-w-extension
                       :atom-request-url (url/atom-request-url context concept-type-w-extension query-string))

        search-params (lp/process-legacy-psa params query-string)
        results (query-svc/find-concepts-by-parameters context concept-type search-params)]
    {:status 200
     :headers {"Content-Type" (str (mt/format->mime-type result-format) "; charset=utf-8")}
     :body results}))

(defn- find-concept-by-cmr-concept-id
  "Invokes query service to find concept metadata by cmr concept id and returns the response"
  [context concept-id headers]
  ;; Note: headers argument is reserved for ACL validation
  (info (format "Search for concept with cmr-concept-id [%s]" concept-id))
  (let [concept (query-svc/find-concept-by-id context concept-id)]
    {:status 200
     :headers {"Content-Type" "application/xml; charset=utf-8"}
     :body (:metadata concept)}))

(def concept-type-w-extension-regex
  "A regular expression that matches URLs including the concept type (pluralized) along with a file
  extension."
  #"(?:(?:granules)|(?:collections))(?:\..+)?")

(defn- build-routes [system]
  (routes
    (context (get-in system [:search-public-conf :relative-root-url]) []

      ;; Retrieve by cmr concept id
      (context "/concepts/:cmr-concept-id" [cmr-concept-id]
        (GET "/" {headers :headers context :request-context}
          (find-concept-by-cmr-concept-id context cmr-concept-id headers)))

      ;; Find concepts
      (context ["/:concept-type-w-extension" :concept-type-w-extension concept-type-w-extension-regex] [concept-type-w-extension]
        (GET "/" {params :params headers :headers context :request-context query-string :query-string}
          (find-concepts context concept-type-w-extension params headers query-string))
        (POST "/" {params :params headers :headers context :request-context body :body-copy}
          (find-concepts context concept-type-w-extension params headers body)))

      ;; reset operation available just for development purposes
      ;; clear the cache for search app
      (POST "/reset" {:keys [request-context]}
        (r/created (query-svc/reset request-context))))
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
