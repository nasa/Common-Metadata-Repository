(ns cmr.search.api.providers
  "Defines the API for provider searches in the CMR."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.search.api.core :as core-api]
   [cmr.search.services.provider-service :as provider-service]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.services.result-format-helper :as rfh]
   [compojure.core :refer :all]))

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
  "Look for and remove metadata field from the body returned by the metadata db
   service, as it is not needed in the legacy responses."
  [response]
  (let [new-body (-> response
                     (as-> body (map :metadata body))
                     json/generate-string)
        new-response {}]
    (assoc new-response :body new-body)))

(defn- pull-metadata-single-provider
 [response]
(let [new-body (-> response
                   :body
                   (json/parse-string true)
                   (:metadata)
                   json/generate-string)]
  (if (not= (:status response) 200)
        (assoc response :body (json/parse-string (:body response) true))
        (assoc response :body new-body))))

(defn- one-result->response-map
  "Returns the response map of the given result, but this expects there to be
   just one value and it only returns the metadata, see result->response-map for
   the older return type. However, if there is an error in the response, then the
   body is returned as is"
  [result]
  (let [{:keys [status body]} result]
    
    {:status status
     :headers {"Content-Type" (mt/with-utf-8 mt/json)}
     :body body}))

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
      (one-result->response-map (pull-metadata-single-provider (provider-service/read-provider request-context provider-id))))

    ;; Return the list of all providers
    (GET "/" {:keys [request-context]}
      (one-result->response-map (pull-metadata (provider-service/get-providers-raw request-context))))))
