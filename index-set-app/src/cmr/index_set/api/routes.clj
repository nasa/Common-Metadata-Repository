(ns cmr.index-set.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [ring.util.response :as r]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.cache :as cache]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [cmr.index-set.services.index-service :as index-svc]
            [cmr.system-trace.http :as http-trace]
            [cmr.acl.core :as acl]))

(def cache-api-routes
  "Create routes for the cache querying/management api"
  (context "/caches" []
    ;; Get the list of caches
    (GET "/" {:keys [params request-context headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)
            caches (map name (keys (get-in context [:system :caches])))]
        (acl/verify-ingest-management-permission context :read)
        {:status 200
         :body (json/generate-string caches)}))
    ;; Get the keys for the given cache
    (GET "/:cache-name" {{:keys [cache-name] :as params} :params
                         request-context :request-context
                         headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)
            cache (cache/context->cache context (keyword cache-name))]
        (acl/verify-ingest-management-permission context :read)
        (when cache
          (let [result (cache/cache-keys cache)]
            {:status 200
             :body (json/generate-string result)}))))

    ;; Get the value for the given key for the given cache
    (GET "/:cache-name/:cache-key" {{:keys [cache-name cache-key] :as params} :params
                                    request-context :request-context
                                    headers :headers}
      (let [cache-key (keyword cache-key)
            context (acl/add-authentication-to-context request-context params headers)
            cache (cache/context->cache context (keyword cache-name))
            result (cache/cache-lookup cache cache-key)]
        (acl/verify-ingest-management-permission context :read)
        (when result
          {:status 200
           :body (json/generate-string result)})))))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/index-sets" []
        (POST "/" {body :body request-context :request-context params :params headers :headers}
          (let [index-set (walk/keywordize-keys body)
                context (acl/add-authentication-to-context request-context params headers)]
            (acl/verify-ingest-management-permission context :update)
            (r/created (index-svc/create-index-set request-context index-set))))

        ;; respond with index-sets in elastic
        (GET "/" {request-context :request-context params :params headers :headers}
          (let [context (acl/add-authentication-to-context request-context params headers)]
            (acl/verify-ingest-management-permission context :read)
            (r/response (index-svc/get-index-sets request-context))))

        (context "/:id" [id]
          (GET "/" {request-context :request-context params :params headers :headers}
            (let [context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission context :read)
              (r/response (index-svc/get-index-set request-context id))))

          (PUT "/" {request-context :request-context body :body params :params headers :headers}
            (let [index-set (walk/keywordize-keys body)
                  context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission context :update)
              (index-svc/update-index-set request-context index-set)
              {:status 200}))

          (DELETE "/" {request-context :request-context params :params headers :headers}
            (let [context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission context :update)
              (index-svc/delete-index-set request-context id)
              {:status 204}))))

      ;; add routes for accessing caches
      cache-api-routes

      (POST "/clear-cache" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (cache/reset-caches context))
        {:status 200})

      ;; delete all of the indices associated with index-sets and index-set docs in elastic
      (POST "/reset" {request-context :request-context params :params headers :headers}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (index-svc/reset request-context)
          {:status 204}))

      (GET "/health" {request-context :request-context params :params}
        (let [{pretty? :pretty} params
              {:keys [ok? dependencies]} (index-svc/health request-context)]
          {:status (if ok? 200 503)
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (json/generate-string dependencies {:pretty pretty?})})))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



