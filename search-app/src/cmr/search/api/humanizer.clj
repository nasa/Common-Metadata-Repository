(ns cmr.search.api.humanizer
  "Defines the API for humanizer in the CMR."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.routes :as cr]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.services.humanizers.humanizer-json-schema-validation :as hv]
   [cmr.search.services.humanizers.humanizer-report-service :as hrs]
   [cmr.search.services.humanizers.humanizer-service :as humanizer-service]
   [compojure.core :refer :all]
   [compojure.route :as route]))

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
  (hv/validate-humanizer-json body)
  (let [result (humanizer-service/update-humanizers context body)
        status-code (if (= 1 (:revision-id result)) 201 200)]
    (humanizer-response status-code result)))

(defn- humanizers-report
  "Handles a request to get a humanizers report"
  [context params]
  (let [regenerate? (= "true" (util/safe-lowercase (:regenerate params)))]
    ;; Only admins can force the humanizer report to be regenerated
    (when regenerate?
      (acl/verify-ingest-management-permission context :update))
    {:status 200
     :headers {cr/CONTENT_TYPE_HEADER mt/csv}
     :body (hrs/humanizers-report-csv context regenerate?)}))

(def humanizers-routes
  "Routes for humanizer endpoints"
  (context "/humanizers" []

    ;; create/update humanizers
    (PUT "/"
         {:keys [request-context headers body]}
         (update-humanizers request-context headers (slurp body)))

    ;; retrieve humanizers
    (GET "/"
         {:keys [request-context]}
         (humanizer-response
          200
          (humanizer-service/get-humanizers request-context)))

    ;; retrieve the humanizers report
    (GET "/report"
         {context :request-context params :params}
         (humanizers-report context params))))
