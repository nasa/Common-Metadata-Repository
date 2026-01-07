(ns cmr.search.api.providers
  "Defines the API for provider searches in the CMR."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.log :refer [info]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.api.core :as core-api]
   [cmr.search.services.provider-service :as provider-service]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.services.result-format-helper :as rfh]
   [compojure.core :refer [GET OPTIONS context]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants

(def CMR_GRANULE_COUNT_HEADER "CMR-Granule-Hits")
(def CMR_COLLECTION_COUNT_HEADER "CMR-Collection-Hits")

(def supported-provider-holdings-mime-types
  "The mime types supported by search."
  #{mt/any
    mt/xml
    mt/json
    mt/csv})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(defn- get-provider-holdings
  "Invokes query service to retrieve provider holdings and returns the response"
  [ctx path-w-extension params headers]
  (let [params (core-api/process-params nil params path-w-extension headers mt/json)
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

(defn- pull-metadata
  "Look for and pull up the metadata field from the body returned by the metadata db
  from search app we want to promote this as the response."
  [response]
  ;; response passed through timing function index [0] time index [1] result
  (let [time-taken (first response)
        response-body (second response)
        hits (count response-body)
        provider-metadata (-> response-body
                              (as-> body (map :metadata body))
                              json/generate-string)
        new-response {}]
    (assoc new-response :hits hits :took time-taken :items (json/parse-string provider-metadata true))))

(defn- pull-metadata-single-provider
  "Look for and remove metadata field from the body returned by the metadata db for a
  specific provider from search app we want to promote this as the response."
 [response]
  ;; response passed through timing function index [0] time index [1] result
(let [time-taken (first response)
      response-body (second response)
      hits 1
      provider-metadata (-> response-body
                            :body
                            (json/parse-string true)
                            (:metadata)
                            ;; Remove the user ids from the output for security reasons
                            (util/remove-nested-key [:Administrators]))
      new-response {}]
  (if (not= (:status response-body) 200)
        (json/parse-string (:body response-body) true)
        (assoc new-response :hits hits :took time-taken :items [provider-metadata]))))

(defn- one-result->response-map
  "Returns the response map of the given result, but this expects there to be
  just one value and it only returns the metadata however, if there is an error in the response,
  then the body is returned as is."
  [result]
  (let [{:keys [status]} result
        response {:headers {"Content-Type" (mt/with-utf-8 mt/json)} :body result}]
    response))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def holdings-routes
  (context ["/:path-w-extension" :path-w-extension #"(?:provider_holdings)(?:\..+)?"] [path-w-extension]
    (OPTIONS "/" req (common-routes/options-response))
    (GET "/"
      {params :params headers :headers ctx :request-context}
      (get-provider-holdings ctx path-w-extension params headers))))

(def provider-api-routes
  (context "/providers" []
    ;; read a provider
    (GET "/:provider-id" {{:keys [provider-id] :as params} :params
                          request-context :request-context
                          headers :headers}
      (->> provider-id
           (provider-service/read-provider request-context)
           (pull-metadata-single-provider)
           (one-result->response-map)))

    ;; Return the list of all providers
    (GET "/" {:keys [request-context]}
      (->> request-context
           (provider-service/get-providers-raw)
           (pull-metadata)
           (one-result->response-map)))))

