(ns cmr.index-queue.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.index-queue.services.index-queue-service :as index-svc]))

(defn- ignore-conflict?
  "Return false if ignore_conflict parameter is set to false; otherwise return true"
  [params]
  (if (= "false" (:ignore_conflict params))
    false
    true))

(defn- build-routes [system]
  (routes
    ;; Index a concept
    (POST "/" {body :body context :request-context params :params headers :headers}
      (let [{:keys [concept-id revision-id]} (walk/keywordize-keys body)
            ignore-conflict (ignore-conflict? params)]
        (index-svc/index-concept context concept-id revision-id ignore-conflict)))

    ; (POST "/reindex-provider-collections"
    ;   {context :request-context params :params headers :headers body :body}
    ;   (let [context (acl/add-authentication-to-context context params headers)]
    ;     (acl/verify-ingest-management-permission context :update)
    ;     (index-svc/reindex-provider-collections
    ;       context
    ;       body))
    ;   {:status 200})

    ; ;; Unindex all concepts within a provider
    ; (context "/provider/:provider-id" [provider-id]
    ;   (DELETE "/" {context :request-context params :params headers :headers}
    ;     (let [context (acl/add-authentication-to-context context params headers)]
    ;       (acl/verify-ingest-management-permission context :update)
    ;       (index-svc/delete-provider context provider-id)
    ;       {:status 200})))

    ; ;; Unindex a concept
    ; (context "/:concept-id/:revision-id" [concept-id revision-id]
    ;   (DELETE "/" {context :request-context params :params headers :headers}
    ;     (let [ignore-conflict (ignore-conflict? params)
    ;           context (acl/add-authentication-to-context context params headers)]
    ;       (index-svc/delete-concept context concept-id revision-id ignore-conflict)
    ;       {:status 204})))

    ; (GET "/health" {request-context :request-context params :params}
    ;   (let [{pretty? :pretty} params
    ;         {:keys [ok? dependencies]} (index-svc/health request-context)]
    ;     {:status (if ok? 200 503)
    ;      :headers {"Content-Type" "application/json; charset=utf-8"}
    ;      :body (json/generate-string dependencies {:pretty pretty?})}))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



