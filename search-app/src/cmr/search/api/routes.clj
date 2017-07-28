(ns cmr.search.api.routes
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.services.search :as search]
   [cmr.common-app.services.search.query-model :as common-qm]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as svc-errors]
   [cmr.common.util :as util]
   [cmr.common.xml :as cx]
   [cmr.search.api.community-usage-metrics :as metrics-api]
   [cmr.search.api.humanizer :as humanizers-api]
   [cmr.search.api.keyword :as keyword-api]
   [cmr.search.api.tags-api :as tags-api]
   [cmr.search.api.variables-api :as variables-api]

   ;; Required here to make sure the multimethod function implementation is available
   [cmr.search.data.elastic-results-to-query-results]

   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]

   ;; Result handlers
   ;; required here to avoid circular dependency in query service
   [cmr.search.results-handlers.atom-json-results-handler]
   [cmr.search.results-handlers.atom-results-handler]
   [cmr.search.results-handlers.csv-results-handler]
   [cmr.search.results-handlers.kml-results-handler]
   [cmr.search.results-handlers.metadata-results-handler]
   [cmr.search.results-handlers.opendata-results-handler]
   [cmr.search.results-handlers.reference-results-handler]
   [cmr.search.results-handlers.tags-json-results-handler]
   [cmr.search.results-handlers.variables-json-results-handler]
   [cmr.search.results-handlers.timeline-results-handler]
   [cmr.search.results-handlers.umm-json-results-handler]

   ;; ACL support. Required here to avoid circular dependencies
   [cmr.search.services.acls.collection-acls]
   [cmr.search.services.acls.granule-acls]

   [cmr.search.services.health-service :as hs]
   [cmr.search.services.parameters.legacy-parameters :as lp]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.services.result-format-helper :as rfh]
   [cmr.umm-spec.versioning :as umm-version]
   [compojure.core :refer :all]
   [inflections.core :as inf]
   [ring.middleware.json :as ring-json]
   [ring.util.codec :as codec]
   [ring.util.request :as request]
   [ring.util.response :as r]))

(def CMR_GRANULE_COUNT_HEADER "CMR-Granule-Hits")
(def CMR_COLLECTION_COUNT_HEADER "CMR-Collection-Hits")

(def search-result-supported-mime-types
  "The mime types supported by search."
  #{mt/any
    mt/xml
    mt/json
    mt/umm-json
    mt/umm-json-results
    mt/legacy-umm-json
    mt/echo10
    mt/dif
    mt/dif10
    mt/atom
    mt/iso19115
    mt/opendata
    mt/csv
    mt/kml
    mt/native})

(def supported-provider-holdings-mime-types
  "The mime types supported by search."
  #{mt/any
    mt/xml
    mt/json
    mt/csv})

(def supported-concept-id-retrieval-mime-types
  {:collection #{mt/any
                 mt/html
                 mt/xml    ; allows retrieving native format
                 mt/native ; retrieve in native format
                 mt/atom
                 mt/json
                 mt/echo10
                 mt/iso19115
                 mt/iso-smap
                 mt/dif
                 mt/dif10
                 mt/umm-json
                 mt/legacy-umm-json}
   :granule #{mt/any
              mt/xml    ; allows retrieving native format
              mt/native ; retrieve in native format
              mt/atom
              mt/json
              mt/echo10
              mt/iso19115
              mt/iso-smap}
   :variable #{mt/any
               mt/umm-json}})

(def find-by-concept-id-concept-types
  #{:collection :granule :variable})

(def granule-parent-collection-params
  "Parameters that signify the need to search collections with data taken from
   a granule search.

  This could be generalized to support parameters that require multiple queries"
  #{:has_granules_created_at :has-granules-created-at})

(defn- concept-type-path-w-extension->concept-type
  "Parses the concept type and extension (\"granules.echo10\") into the concept type"
  [concept-type-w-extension]
  (-> #"^([^s]+)s(?:\..+)?"
      (re-matches concept-type-w-extension)
      second
      keyword))

(defn path-w-extension->concept-id
  "Parses the path-w-extension to remove the concept id from the beginning"
  [path-w-extension]
  (second (re-matches #"([^\.]+?)(?:/[0-9]+)?(?:\..+)?" path-w-extension)))

(defn path-w-extension->revision-id
  "Parses the path-w-extension to extract the revision id. URL path should be of the form
  :concept-id[/:revision-id][.:format], e.g., http://localohst:3003/concepts/C120000000-PROV1/2.xml."
  [path-w-extension]
  (when-let [revision-id (nth (re-matches #"([^\.]+)/([^\.]+)(?:\..+)?" path-w-extension) 2)]
    (try
      (when revision-id
        (Integer/parseInt revision-id))
      (catch NumberFormatException e
        (svc-errors/throw-service-error
          :invalid-data
          (format "Revision id [%s] must be an integer greater than 0." revision-id))))))

(defn- get-search-results-format
  "Returns the requested search results format parsed from headers or from the URL extension,
  The search result format is keyword for any format other than umm-json. For umm-json,
  it is a map in the format of {:format :umm-json :version \"1.2\"}"
  ([path-w-extension headers default-mime-type]
   (get-search-results-format
     path-w-extension headers search-result-supported-mime-types default-mime-type))
  ([path-w-extension headers valid-mime-types default-mime-type]
   (let [result-format (mt/mime-type->format
                         (or (mt/path->mime-type path-w-extension valid-mime-types)
                             (mt/extract-header-mime-type valid-mime-types headers "accept" true)
                             (mt/extract-header-mime-type valid-mime-types headers "content-type" false))
                         default-mime-type)]
     (if (contains? #{:umm-json :umm-json-results} result-format)
       {:format result-format
        :version (or (mt/version-of (mt/get-header headers "accept"))
                     umm-version/current-version)}
       result-format))))

(defn- process-params
  "Processes the parameters by removing unecessary keys and adding other keys like result format."
  [params ^String path-w-extension headers default-mime-type]
  (let [result-format (get-search-results-format path-w-extension headers default-mime-type)
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

(defn- add-scroll-id-to-cache
  "Adds the given ES scroll-id to the cache and returns the generated key"
  [context scroll-id]
  (when scroll-id
    (let [short-scroll-id (str (hash scroll-id))
          id-cache (cache/context->cache context search/scroll-id-cache-key)]
      (cache/set-value id-cache short-scroll-id scroll-id)
      short-scroll-id)))

(defn- search-response
  "Returns the response map for finding concepts"
  [context response]
  (let [short-scroll-id (add-scroll-id-to-cache context (:scroll-id response))
        response (-> response
                     (update :result mt/format->mime-type)
                     (update :scroll-id (constantly short-scroll-id)))]
    (common-routes/search-response response)))

(defn- find-concepts-by-json-query
  "Invokes query service to parse the JSON query, find results and return the response."
  [ctx path-w-extension params headers json-query]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        params (process-params params path-w-extension headers mt/xml)
        _ (info (format "Searching for %ss from client %s in format %s with JSON %s and query parameters %s."
                        (name concept-type) (:client-id ctx)
                        (rfh/printable-result-format (:result-format params)) json-query params))
        results (query-svc/find-concepts-by-json-query ctx concept-type params json-query)]
    (search-response ctx results)))

(defn- find-concepts-by-parameters
  "Invokes query service to parse the parameters query, find results, and return the response"
  [ctx path-w-extension params headers body]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        short-scroll-id (get headers (string/lower-case common-routes/SCROLL_ID_HEADER))
        scroll-id (get-scroll-id-from-cache ctx short-scroll-id)
        ctx (assoc ctx :query-string body :scroll-id scroll-id)
        params (process-params params path-w-extension headers mt/xml)
        result-format (:result-format params)
        _ (info (format "Searching for %ss from client %s in format %s with params %s."
                        (name concept-type) (:client-id ctx)
                        (rfh/printable-result-format result-format) (pr-str params)))
        search-params (lp/process-legacy-psa params)
        results (query-svc/find-concepts-by-parameters ctx concept-type search-params)]
    (search-response ctx results)))

(defn- get-bucket-values-from-aggregation
  "Return a collection of values from aggregation buckets"
  [aggregation-search-results aggregation-name]
  (let [buckets (get-in aggregation-search-results [:aggregations aggregation-name :buckets])]
    (map :key buckets)))

(defn- find-granule-parent-collections
  "Invokes query service to find collections based on data found in a granule search,
   then executes a search for collections with the found concept-ids. Supports CMR Harvesting."
  [ctx path-w-extension params headers body]
  (let [concept-type (concept-type-path-w-extension->concept-type path-w-extension)
        ctx (assoc ctx :query-string body)
        params (process-params params path-w-extension headers mt/xml)
        collections-with-new-granules-search (query-svc/get-collections-from-new-granules ctx params)
        collection-ids (get-bucket-values-from-aggregation collections-with-new-granules-search :collections)
        search-params (-> params
                          (assoc :echo-collection-id collection-ids)
                          (dissoc :has_granules_created_at)
                          (dissoc :has-granules-created-at)
                          lp/process-legacy-psa)]

    (if (empty? collection-ids)
      ;; collections-with-new-granules-search already has an empty response object,
      ;; as well as time-took, which is why it's being passed to search-response here
      (search-response ctx (dissoc collections-with-new-granules-search :aggregations))
      (find-concepts-by-parameters ctx path-w-extension search-params headers body))))

(defn- granule-parent-collection-query?
  "Return true if parameters match any from the list of `multi-part-query-params`
   defined above. Supports CMR Harvesting."
  [search-params]
  (boolean (some granule-parent-collection-params (keys search-params))))

(defn- find-concepts
  "Invokes query service to find results and returns the response.

  This function supports several cases for obtaining concept data:
  * By JSON query
  * By parameter string and URL parameters
  * Collections from Granules - due to the fact that ES doesn't suport joins in the way
    that we need, we have to make two queries here to support CMR Harvesting. This can
    later be generalized easily, should the need arise."
  [ctx path-w-extension params headers body]
  (let [content-type-header (get headers (string/lower-case common-routes/CONTENT_TYPE_HEADER))]
    (cond
      (= mt/json content-type-header)
      (find-concepts-by-json-query ctx path-w-extension params headers body)

      (granule-parent-collection-query? params)
      (find-granule-parent-collections ctx path-w-extension params headers body)

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
  (let [params (process-params params path-w-extension headers mt/json)
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
  (let [params (process-params params path-w-extension headers mt/xml)
        _ (info (format "Searching for concepts from client %s in format %s with AQL: %s and query parameters %s."
                        (:client-id ctx) (rfh/printable-result-format (:result-format params)) aql params))
        results (query-svc/find-concepts-by-aql ctx params aql)]
    (search-response ctx results)))

(defn- find-concept-by-cmr-concept-id
  "Invokes query service to find concept metadata by cmr concept id (and possibly revision id)
  and returns the response"
  [ctx path-w-extension params headers]
  (let [concept-id (path-w-extension->concept-id path-w-extension)
        revision-id (path-w-extension->revision-id path-w-extension)
        concept-type (concepts/concept-id->type concept-id)
        concept-type-supported-mime-types (supported-concept-id-retrieval-mime-types concept-type)]
    (when-not (contains? find-by-concept-id-concept-types concept-type)
      (svc-errors/throw-service-error
        :bad-request
        (format "Retrieving concept by concept id is not supported for concept type [%s]."
                (name concept-type))))

    (if revision-id
      ;; We don't support Atom or JSON (yet) for lookups that include revision-id due to
      ;; limitations of the current transformer implementation. This will be fixed with CMR-1935.
      (let [supported-mime-types (disj concept-type-supported-mime-types mt/atom mt/json)
            result-format (get-search-results-format path-w-extension headers
                                                     supported-mime-types
                                                     mt/native)
            ;; XML means native in this case
            result-format (if (= result-format :xml) :native result-format)]
        (info (format "Search for concept with cmr-concept-id [%s] and revision-id [%s]"
                      concept-id
                      revision-id))
        ;; else, revision-id is nil
        (search-response ctx (query-svc/find-concept-by-id-and-revision
                              ctx
                              result-format
                              concept-id
                              revision-id)))
      (let [result-format (get-search-results-format path-w-extension headers
                                                     concept-type-supported-mime-types
                                                     mt/native)
            ;; XML means native in this case
            result-format (if (= result-format :xml) :native result-format)]
        (info (format "Search for concept with cmr-concept-id [%s]" concept-id))
        (search-response ctx (query-svc/find-concept-by-id ctx result-format concept-id))))))

(defn- get-deleted-collections
  "Invokes query service to search for collections that are deleted and returns the response"
  [ctx path-w-extension params headers]
  (let [params (process-params params path-w-extension headers mt/xml)]
    (info (format "Searching for deleted collections from client %s in format %s with params %s."
                  (:client-id ctx) (rfh/printable-result-format (:result-format params))
                  (pr-str params)))
    (search-response ctx (query-svc/get-deleted-collections ctx params))))

(defn- get-provider-holdings
  "Invokes query service to retrieve provider holdings and returns the response"
  [ctx path-w-extension params headers]
  (let [params (process-params params path-w-extension headers mt/json)
        _ (info (format "Searching for provider holdings from client %s in format %s with params %s."
                        (:client-id ctx) (rfh/printable-result-format (:result-format params))
                        (pr-str params)))
        [provider-holdings provider-holdings-formatted]
        (query-svc/get-provider-holdings ctx params)
        collection-count (count provider-holdings)
        granule-count (reduce + (map :granule-count provider-holdings))]
    {:status 200
     :headers {common-routes/CONTENT_TYPE_HEADER (str (mt/format->mime-type (:result-format params)) "; charset=utf-8")
               CMR_COLLECTION_COUNT_HEADER (str collection-count)
               CMR_GRANULE_COUNT_HEADER (str granule-count)
               common-routes/CORS_ORIGIN_HEADER "*"}
     :body provider-holdings-formatted}))

(defn- find-tiles
  "Retrieves all the tiles which intersect the input geometry"
  [ctx params]
  (let [results (query-svc/find-tiles-by-geometry ctx params)]
    {:status 200
     :headers {common-routes/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)}
     :body results}))

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      (context relative-root-url []
        ;; Add routes for tagging
        tags-api/tag-api-routes

        ;; Add routes for variable association
        variables-api/variable-api-routes

        ;; Add routes for humanizers
        humanizers-api/humanizers-routes

        ;; Add routes for community usage metrics
        metrics-api/community-usage-metrics-routes

        ;; Retrieve by cmr concept id or concept id and revision id
        ;; Matches URL paths of the form /concepts/:concept-id[/:revision-id][.:format],
        ;; e.g., http://localhost:3003/concepts/C120000000-PROV1,
        ;;       http://localhost:3003/concepts/C120000000-PROV1/2
        ;;       http://localohst:3003/concepts/C120000000-PROV1/2.xml
        (context ["/concepts/:path-w-extension" :path-w-extension #"[A-Z][A-Z]?[0-9]+-[0-9A-Z_]+.*"] [path-w-extension]
          ;; OPTIONS method is needed to support CORS when custom headers are used in requests to
          ;; the endpoint. In this case, the Echo-Token header is used in the GET request.
          (OPTIONS "/" req common-routes/options-response)
          (GET "/"
              {params :params headers :headers ctx :request-context}
              (find-concept-by-cmr-concept-id ctx path-w-extension params headers)))

        ;; Find concepts
        (context ["/:path-w-extension" :path-w-extension #"(?:(?:granules)|(?:collections))(?:\..+)?"] [path-w-extension]
          (OPTIONS "/" req common-routes/options-response)
          (GET "/"
               {params :params headers :headers ctx :request-context query-string :query-string}
               (find-concepts ctx path-w-extension params headers query-string))
          ;; Find concepts - form encoded or JSON
          (POST "/"
                {params :params headers :headers ctx :request-context body :body-copy}
                (find-concepts ctx path-w-extension params headers body)))

        ;; Granule timeline
        (context ["/granules/:path-w-extension" :path-w-extension #"(?:timeline)(?:\..+)?"] [path-w-extension]
          (OPTIONS "/" req common-routes/options-response)
          (GET "/"
               {params :params headers :headers ctx :request-context query-string :query-string}
               (get-granules-timeline ctx path-w-extension params headers query-string))
          (POST "/" {params :params headers :headers ctx :request-context body :body-copy}
            (get-granules-timeline ctx path-w-extension params headers body)))

        (context ["/:path-w-extension" :path-w-extension #"(?:deleted-collections)(?:\..+)?"] [path-w-extension]
          (OPTIONS "/" req common-routes/options-response)
          (GET "/"
               {params :params headers :headers ctx :request-context}
               (get-deleted-collections ctx path-w-extension params headers)))

        ;; AQL search - xml
        (context ["/concepts/:path-w-extension" :path-w-extension #"(?:search)(?:\..+)?"] [path-w-extension]
          (OPTIONS "/" req common-routes/options-response)
          (POST "/"
                {params :params headers :headers ctx :request-context body :body-copy}
                (find-concepts-by-aql ctx path-w-extension params headers body)))

        ;; Provider holdings
        (context ["/:path-w-extension" :path-w-extension #"(?:provider_holdings)(?:\..+)?"] [path-w-extension]
          (OPTIONS "/" req common-routes/options-response)
          (GET "/"
               {params :params headers :headers ctx :request-context}
               (get-provider-holdings ctx path-w-extension params headers)))

        ;; Resets the application back to it's initial state.
        (POST "/reset"
              {ctx :request-context}
              (acl/verify-ingest-management-permission ctx)
              (cache/reset-caches ctx)
              {:status 204})

        ;; Add routes for retrieving GCMD keywords
        keyword-api/keyword-api-routes

        ;; add routes for managing jobs
        (common-routes/job-api-routes
         (routes
           (POST "/refresh-collection-metadata-cache"
                 {ctx :request-context}
                 (acl/verify-ingest-management-permission ctx :update)
                 (metadata-cache/refresh-cache ctx)
                 {:status 200})))

        ;; add routes for accessing caches
        common-routes/cache-api-routes

        ;; add routes for checking health of the application
        (common-health/health-api-routes hs/health)

        ;; add routes for enabling/disabling application
        (common-enabled/write-enabled-api-routes
         #(acl/verify-ingest-management-permission % :update))

        (GET "/tiles"
             {params :params ctx :request-context}
             (find-tiles ctx params))))))
