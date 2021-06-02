(ns cmr.ingest.routes
  "This namespace is the one responsible for combining the routes intended for
  use by libraries and applications (API routes) and the routes intended for
  human, browser, or web crawler consumption (site routes). It also provides
  a place for adding routes that apply to both (e.g., robots.txt, should we
  ever need that for ingest)."
  (:require
   [cmr.acl.core :as acl]
   [cmr.ingest.api.multipart :as mp]
   [cmr.ingest.api.routes :as api-routes]
   [cmr.common-app.api.request-context-user-augmenter :as context-augmenter]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.common.api.context :as context]
   [cmr.ingest.site.routes :as site-routes]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.mime-types :as mt]
   [compojure.core :refer [routes]]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn build-routes [system]
  (routes
    (api-routes/build-routes system)
    (site-routes/build-routes system)
    (common-pages/not-found)))

(defn default-error-format
  "Determine the format that errors should be returned in based on the default-format
  key set on the ExceptionInfo object passed in as parameter e. Defaults to json if
  the default format has not been set to :xml."
  [_request e]
  (or (mt/format->mime-type (:default-format (ex-data e))) mt/json))

(defn handlers [system]
  (-> (build-routes system)
      ;; add-authentication-handler adds the token and client id for user to
      ;; the context add-user-id-and-sids-handler lazy adds the user id and
      ;; sids for that token Need to maintain this order (works backwards).
      context-augmenter/add-user-id-and-sids-handler
      acl/add-authentication-handler
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      api-errors/invalid-url-encoding-handler
      mp/wrap-multipart-params
      (api-errors/exception-handler default-error-format)
      common-routes/add-request-id-response-handler
      common-routes/add-security-header-response-handler
      (context/build-request-context-handler system)
      common-routes/pretty-print-response-handler
      params/wrap-params))
