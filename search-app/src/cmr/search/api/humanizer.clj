(ns cmr.search.api.humanizer
  "Defines the API for humanizer in the CMR."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.common.mime-types :as mt]
            [cmr.common-app.api.routes :as cr]
            [cmr.search.services.humanizers.humanizer-service :as humanizer-service]
            [cmr.search.services.humanizers.humanizer-report-service :as hrs]
            [cmr.common.services.errors :as errors]
            [cmr.acl.core :as acl]))

(defn- validate-humanizer-content-type
  "Validates that content type sent with humanizer is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- humanizer-response
  "Creates a successful humanizer response with the given data response"
  [status-code data]
  {:status status-code
   :body (json/generate-string data)
   :headers {cr/CONTENT_TYPE_HEADER mt/json}})

(defn- update-humanizers
  "Processes a humanizer update request."
  [context headers body]
  (acl/verify-ingest-management-permission context :update)
  (validate-humanizer-content-type headers)
  (let [result (humanizer-service/update-humanizers context body)
        status-code (if (= 1 (:revision-id result)) 201 200)]
    (humanizer-response status-code result)))

(defn- update-community-usage
  "Processes a community usage update request"
  [context headers body]
  (acl/verify-ingest-management-permission context :update)
  (mt/extract-header-mime-type #{mt/csv} headers "content-type" true)
  (let [result (humanizer-service/update-community-usage context body)
        status-code (if (= 1 (:revision-id result)) 201 200)]
    (humanizer-response status-code result)))

(defn- humanizers-report
  "Handles a request to get a humanizers report"
  [context]
  {:status 200
   :headers {cr/CONTENT_TYPE_HEADER mt/csv}
   :body (hrs/humanizers-report-csv context)})

(def humanizers-routes
  (context "/humanizers" []

    ;; create/update humanizers
    (PUT "/" {:keys [request-context headers body]}
      (update-humanizers request-context headers (slurp body)))

    ;; retrieve humanizers
    (GET "/" {:keys [request-context]}
      (humanizer-response 200 (humanizer-service/get-humanizers request-context)))

    ;; retrieve the humanizers report
    (GET "/report" {context :request-context}
            (humanizers-report context))))

(def community-usage-metrics-routes
  (context "/community-usage-metrics" []

    ;; create/update community usage metrics
    (PUT "/" {:keys [request-context headers body]}
      (update-community-usage request-context headers (slurp body)))

    ;; retrieve community usage metrics
    (GET "/" {:keys [request-context]}
      (humanizer-response 200 (humanizer-service/get-community-usage-metrics request-context)))))
