(ns cmr.indexer.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [ring.util.response :as r]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.cache :as cache]
            [cmr.acl.core :as acl]

            ;; These must be required here to make multimethod implementations available.
            [cmr.indexer.data.concepts.collection]
            [cmr.indexer.data.concepts.granule]

            [cmr.indexer.services.index-service :as index-svc]
            [cmr.system-trace.http :as http-trace]))

(def cache-api-routes
  "Create routes for the cache querying/management api"
  (context "/caches" []
    ;; Get the list of caches
    (GET "/" {:keys [params request-context headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [caches (map name (keys (get-in context [:system :caches])))]
          (acl/verify-ingest-management-permission context :read)
          {:status 200
           :body (json/generate-string caches)})))
    ;; Get the keys for the given cache
    (GET "/:cache-name" {{:keys [cache-name] :as params} :params
                         request-context :request-context
                         headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [cache (cache/context->cache context (keyword cache-name))]
          (when cache
            (let [result (cache/cache-keys cache)]
              {:status 200
               :body (json/generate-string result)})))))

    ;; Get the value for the given key for the given cache
    (GET "/:cache-name/:cache-key" {{:keys [cache-name cache-key] :as params} :params
                                    request-context :request-context
                                    headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [cache-key (keyword cache-key)
              cache (cache/context->cache context (keyword cache-name))
              result (cache/cache-lookup cache cache-key)]
          (when result
            {:status 200
             :body (json/generate-string result)}))))

    (POST "/clear-cache" {:keys [request-context params headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (cache/reset-caches context))
      {:status 200})))


(defn- ignore-conflict?
  "Return false if ignore_conflict parameter is set to false; otherwise return true"
  [params]
  (if (= "false" (:ignore_conflict params))
    false
    true))

;; Note for future. We should cleanup this API. It's not very well layed out.
(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      ;; Index a concept
      (POST "/" {body :body context :request-context params :params headers :headers}
        (let [{:keys [concept-id revision-id]} (walk/keywordize-keys body)
              ignore-conflict (ignore-conflict? params)
              context (acl/add-authentication-to-context context params headers)]
          (r/created (index-svc/index-concept context concept-id revision-id ignore-conflict))))

      ;; reset operation available just for development purposes
      ;; delete configured elastic indexes and create them back
      (POST "/reset" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (cache/reset-caches request-context)
          (index-svc/reset context))
        {:status 204})

      ;; Sends an update to the index set to update mappings and index settings.
      (POST "/update-indexes" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (index-svc/update-indexes context))
        {:status 200})

      ;; add routes for accessing caches
      cache-api-routes

      (POST "/reindex-provider-collections"
        {context :request-context params :params headers :headers body :body}
        (let [context (acl/add-authentication-to-context context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (index-svc/reindex-provider-collections
            context
            body))
        {:status 200})

      ;; Unindex all concepts within a provider
      (context "/provider/:provider-id" [provider-id]
        (DELETE "/" {context :request-context params :params headers :headers}
          (let [context (acl/add-authentication-to-context context params headers)]
            (acl/verify-ingest-management-permission context :update)
            (index-svc/delete-provider context provider-id)
            {:status 200})))

      ;; Unindex a concept
      (context "/:concept-id/:revision-id" [concept-id revision-id]
        (DELETE "/" {context :request-context params :params headers :headers}
          (let [ignore-conflict (ignore-conflict? params)
                context (acl/add-authentication-to-context context params headers)]
            (index-svc/delete-concept context concept-id revision-id ignore-conflict)
            {:status 204})))

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
      (errors/exception-handler (fn [_] "application/json"))
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



