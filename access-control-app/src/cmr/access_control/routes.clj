(ns cmr.access-control.routes
  "This namespace is the one responsible for combining the routes intended for
  use by libraries and applications (API routes) and the routes intended for
  human, browser, or web crawler consumption (site routes). It also provides
  a place for adding routes that apply to both (e.g., robots.txt, should we
  ever need that for access-control)."
  (:require
   [clojure.edn :as edn]
   [cmr.access-control.api.routes :as api-routes]
   [cmr.access-control.site.routes :as site-routes]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.request-logger :as req-log]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.log :refer (warnf)]
   [compojure.core :refer [routes]]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn build-routes [system]
  (routes
    (api-routes/build-routes system)
    (site-routes/build-routes system)
    (common-pages/not-found)))

(defn- parse-group-permission
  "Safely parse group-permission parameter, handling both parsed and string formats."
  [group-permission]
  (cond
    (nil? group-permission) nil
    (map? group-permission) group-permission
    (string? group-permission)
    (try
      (edn/read-string {:readers {}} group-permission)
      (catch Exception _e
        (warnf "Failed to parse group-permission parameter: %s." group-permission)
        nil))
    :else
    (do
      (warnf "Unexpected group-permission format: %s." (type group-permission))
      nil)))

(defn normalize-acl-search-params
  "Normalize params to handle format changes from HTTP library updates."
  [params]
  (if-let [gp (:group-permission params)]
    (assoc params :group-permission (parse-group-permission gp))
    params))

(defn wrap-parse-group-permission
  "Middleware to parse group-permission params that may come as strings."
  [handler]
  (fn [request]
    (let [params (:params request)
          normalized (normalize-acl-search-params params)
          request' (assoc request :params normalized)]
      (handler request'))))

(defn handlers [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      common-routes/add-request-id-response-handler
      wrap-parse-group-permission
      req-log/log-ring-request ;; Must be after request id
      (context/build-request-context-handler system)
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      api-errors/invalid-url-encoding-handler
      api-errors/exception-handler
      common-routes/pretty-print-response-handler
      params/wrap-params
      req-log/add-body-hashes
      req-log/log-ring-request
      req-log/add-time-stamp
      ;; Last in line, but really first for request as they process in reverse
      (common-routes/wrap-disable-read-eval)))
