(ns cmr.search.api.community-usage-metrics
  "Defines the API for community usage metrics in the CMR. Community usage metrics come from the EMS
   as a CSV and are stored in the CMR as JSON with the humanizers. The metrics are used for relevancy
   scoring based on popularity."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.routes :as cr]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.search.services.community-usage-metrics.metrics-service :as metrics-service]
   [compojure.core :refer :all]
   [compojure.route :as route]))

(defn- community-usage-metrics-response
  "Creates a successful community usage metrics response with the given data response"
  [status-code data]
  {:status status-code
   :body (json/generate-string data)
   :headers {cr/CONTENT_TYPE_HEADER mt/json}})

(defn- update-community-usage
  "Processes a community usage update request"
  [context headers body]
  (acl/verify-ingest-management-permission context :update)
  (mt/extract-header-mime-type #{mt/csv} headers "content-type" true)
  (let [result (metrics-service/update-community-usage context body)
        status-code (if (= 1 (:revision-id result)) 201 200)]
    (community-usage-metrics-response status-code result)))

(def community-usage-metrics-routes
  "Routes for community usage metrics endpoints"
  (context "/community-usage-metrics" []

    ;; create/update community usage metrics
    (PUT "/" {:keys [request-context headers body]}
      (update-community-usage request-context headers (slurp body)))

    ;; retrieve community usage metrics
    (GET "/" {:keys [request-context]}
      (community-usage-metrics-response 200 (metrics-service/get-community-usage-metrics request-context)))))
