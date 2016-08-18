(ns cmr.search.api.humanizer
  "Defines the API for humanizer in the CMR."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.common.mime-types :as mt]
            [cmr.search.services.humanizer-service :as humanizer-service]
            [cmr.common.services.errors :as errors]
            [cmr.acl.core :as acl]))

(defn- validate-humanizer-content-type
  "Validates that content type sent with humanizer is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- humanizer-response
  "Creates a successful humanizer response with the given data response"
  [data]
  {:status 200
   :body (json/generate-string data)
   :headers {"Content-Type" mt/json}})

(defn update-humanizer
  "Processes a humanizer update request."
  [context headers body]
  (acl/verify-ingest-management-permission context :update)
  (validate-humanizer-content-type headers)
  (humanizer-response (humanizer-service/update-humanizer context body)))

(defn get-humanizer
  "Retrieves the humanizer from metadata-db, not the cache."
  [context]
  (humanizer-response (humanizer-service/get-humanizer-json context)))

(def humanizer-routes
  (context "/humanizer" []

    ;; create/update humanizer
    (PUT "/" {:keys [request-context headers body]}
      (update-humanizer request-context headers (slurp body)))

    ;; retrieve humanizer
    (GET "/" {:keys [request-context]}
      (get-humanizer request-context))))
