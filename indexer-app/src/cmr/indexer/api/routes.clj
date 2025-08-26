(ns cmr.indexer.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [clojure.walk :as walk]
   [compojure.core :refer [DELETE GET POST PUT context routes]]
   [compojure.handler :as handler]
   [compojure.route :as route]
   [cmr.acl.core :as acl] ;; These must be required here to make defmulti implementations available.
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as errors]
   [cmr.common.cache :as cache]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.request-logger :as req-log]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.elastic-utils.config :as es-config]
   [cmr.indexer.data.concepts.collection]
   [cmr.indexer.data.concepts.granule]
   [cmr.indexer.data.concepts.subscription]
   [cmr.indexer.data.concepts.tag]
   [cmr.indexer.services.index-service :as index-svc]
   [cmr.indexer.services.index-set-service :as index-set-svc]
   [ring.middleware.json :as ring-json]
   [ring.util.response :as r]))

(defn- ignore-conflict?
  "Return false if ignore_conflict parameter is set to false; otherwise return true"
  [params]
  (if (= "false" (:ignore_conflict params))
    false
    true))

(def ^:private index-set-routes
  "Routes providing index-set operations"
  (context "/index-sets" []
    (POST "/" {body :body request-context :request-context}
      (let [index-set (walk/keywordize-keys body)
            _ (acl/verify-ingest-management-permission request-context :update)
            _ (index-set-svc/validate-requested-index-set request-context es-config/gran-elastic-name index-set false)
            _ (index-set-svc/validate-requested-index-set request-context es-config/elastic-name index-set false)
            gran-index-set-resp (index-set-svc/create-index-set request-context es-config/gran-elastic-name index-set)
            non-gran-index-set-resp (index-set-svc/create-index-set request-context es-config/elastic-name index-set)]
        (cond
          (some? gran-index-set-resp) (r/created gran-index-set-resp)
          (some? non-gran-index-set-resp) (r/created non-gran-index-set-resp)
          :else (r/created nil))))

    ;; respond with index-sets in elastic
    (GET "/" {request-context :request-context}
      (acl/verify-ingest-management-permission request-context :read)
      (r/response (index-set-svc/get-all-index-sets request-context)))

    (POST "/reset" {request-context :request-context}
      (acl/verify-ingest-management-permission request-context :update)
      (cache/reset-caches request-context)
      ;; TODO 10636 maybe rename this func. It doesn't reset the index-set to any specific base state, it just deletes it...
      (index-set-svc/reset request-context)
      {:status 204})

    ;; TODO add sys tests for this new endpoint
    ;(context "/:es-cluster-name" [es-cluster-name]
    ;  (GET "/" {request-context :request-context}
    ;    (acl/verify-ingest-management-permission request-context :read)
    ;    (r/response (index-set-svc/get-index-sets request-context es-cluster-name))))

    (context "/:id" [id]
      (GET "/" {request-context :request-context}
        (acl/verify-ingest-management-permission request-context :read)
        (let [gran-index-set (index-set-svc/get-index-set request-context es-config/gran-elastic-name id)
              non-gran-index-set (index-set-svc/get-index-set request-context es-config/elastic-name id)
              combined-index-set (cmr.indexer.services.index-set-service/deep-merge gran-index-set non-gran-index-set)]
          (r/response combined-index-set)))

      (PUT "/" {request-context :request-context body :body}
        (let [index-set (walk/keywordize-keys body)]
          (acl/verify-ingest-management-permission request-context :update)
          (index-set-svc/validate-requested-index-set request-context es-config/gran-elastic-name index-set true)
          (index-set-svc/validate-requested-index-set request-context es-config/elastic-name index-set true)
          (index-set-svc/create-or-update-index-set request-context es-config/gran-elastic-name index-set)
          (index-set-svc/create-or-update-index-set request-context es-config/elastic-name index-set)
          {:status 200}))

      (DELETE "/" {request-context :request-context}
        (acl/verify-ingest-management-permission request-context :update)
        (index-set-svc/delete-index-set request-context id es-config/gran-elastic-name)
        (index-set-svc/delete-index-set request-context id es-config/elastic-name)
        {:status 204})

      (context "/rebalancing-collections/:concept-id" [concept-id]
        ;; Marks the collection as re-balancing in the index set.
        (POST "/start" {request-context :request-context params :params}
          (acl/verify-ingest-management-permission request-context :update)
          (index-set-svc/mark-collection-as-rebalancing request-context id concept-id (:target params))
          {:status 200})

        ;; Update the status of collection being rebalanced
        (POST "/update-status" {request-context :request-context params :params}
          (acl/verify-ingest-management-permission request-context :update)
          (index-set-svc/update-collection-rebalancing-status request-context id concept-id (:status params))
          {:status 200})

        ;; Marks the collection as completed rebalancing
        (POST "/finalize" {request-context :request-context}
          (acl/verify-ingest-management-permission request-context :update)
          (index-set-svc/finalize-collection-rebalancing request-context id concept-id)
          {:status 200})))))

;; Note for future. We should cleanup this API. It's not very well layed out.
(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []

      ;; Routes for index-set services
      index-set-routes

       ;; for NGAP deployment health check
      (GET "/" {} {:status 200})

      ;; Index a concept
      (POST "/" {body :body context :request-context params :params}
        (let [{:keys [concept-id revision-id]} (walk/keywordize-keys body)
              options {:ignore-conflict? (ignore-conflict? params)}]
          ;; indexing all revisions index, does nothing for concept types that do not support all revisions index
          (index-svc/index-concept-by-concept-id-revision-id
            context concept-id revision-id (assoc options :all-revisions-index? true))
          ;; indexing concept index
          (r/created (index-svc/index-concept-by-concept-id-revision-id
                       context concept-id revision-id (assoc options :all-revisions-index? false)))))

      ;; reset operation available just for development purposes
      ;; delete configured elastic indexes and create them back
      (POST "/reset" {:keys [request-context]}
        (acl/verify-ingest-management-permission request-context :update)
        (cache/reset-caches request-context)
        (index-svc/reset request-context)
        {:status 204})

      ;; Sends an update to the index set to update mappings and index settings.
      (POST "/update-indexes" {:keys [request-context params]}
        (acl/verify-ingest-management-permission request-context :update)
        (index-svc/update-indexes request-context params)
        {:status 200})

      ;; This is just an alias for /update-indexes to make it easy to update indexes
      ;; after a deployment using the same deployment code that other apps use for db-migrate.
      (POST "/db-migrate" {:keys [request-context params]}
        (acl/verify-ingest-management-permission request-context :update)
        (index-svc/update-indexes request-context params)
        {:status 200})

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      (POST "/reindex-provider-collections" {:keys [request-context body]}
        (acl/verify-ingest-management-permission request-context :update)
        (index-svc/reindex-provider-collections request-context body)
        {:status 200})

      (POST "/reindex-tags" {:keys [request-context]}
        (acl/verify-ingest-management-permission request-context :update)
        (index-svc/reindex-tags request-context)
        {:status 200})

      ;; Unindex all concepts within a provider
      (context "/provider/:provider-id" [provider-id]
        (DELETE "/" {:keys [request-context]}
          (acl/verify-ingest-management-permission request-context :update)
          (index-svc/delete-provider request-context provider-id)
          {:status 200}))

      ;; Unindex a concept
      (context "/:concept-id/:revision-id" [concept-id revision-id]
        (DELETE "/" {:keys [request-context params]}
          (let [options {:ignore_conflict? (ignore-conflict? params)}]
            (index-svc/delete-concept
              request-context concept-id revision-id (assoc options :all-revisions-index? true))
            (index-svc/delete-concept
              request-context concept-id revision-id (assoc options :all-revisions-index? false))
            {:status 204})))

      (common-health/health-api-routes index-svc/health))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      common-routes/add-request-id-response-handler
      req-log/log-ring-request ;; Must be after request id
      acl/add-authentication-handler
      errors/invalid-url-encoding-handler
      errors/exception-handler
      (context/build-request-context-handler system)
      handler/site
      common-routes/pretty-print-response-handler
      ring-json/wrap-json-body
      ring-json/wrap-json-response
      req-log/add-body-hashes
      ;; Last in line, but really first for request as they process in reverse
      req-log/add-time-stamp))
