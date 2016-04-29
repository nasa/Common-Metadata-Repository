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
            [cmr.indexer.data.collection-granule-aggregation-cache :as cgac]

            ;; These must be required here to make multimethod implementations available.
            [cmr.indexer.data.concepts.collection]
            [cmr.indexer.data.concepts.granule]
            [cmr.indexer.data.concepts.tag]

            [cmr.indexer.services.index-service :as index-svc]
            [cmr.common.api.context :as context]
            [cmr.common-app.api.routes :as common-routes]))

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

      ;; TEMPORARY CODE - Remove this after EI-3988 is fixed.
      (POST "/wait/:n" [n]
        (info "Waiting" n "seconds to respond")
        (Thread/sleep (* (Long. n) 1000))
        {:status 200})

      ;; Index a concept
      (POST "/" {body :body context :request-context params :params headers :headers}
        (let [{:keys [concept-id revision-id]} (walk/keywordize-keys body)
              options {:ignore_conflict? (ignore-conflict? params)}]
          ;; indexing all revisions index, does nothing for concept types that do not support all revisions index
          (index-svc/index-concept-by-concept-id-revision-id
            context concept-id revision-id (assoc options :all-revisions-index? true))
          ;; indexing concept index
          (r/created (index-svc/index-concept-by-concept-id-revision-id
                       context concept-id revision-id (assoc options :all-revisions-index? false)))))

      ;; reset operation available just for development purposes
      ;; delete configured elastic indexes and create them back
      (POST "/reset" {:keys [request-context params headers]}
        (acl/verify-ingest-management-permission request-context :update)
        (cache/reset-caches request-context)
        (index-svc/reset request-context)
        {:status 204})

      ;; Sends an update to the index set to update mappings and index settings.
      (POST "/update-indexes" {:keys [request-context params headers]}
        (acl/verify-ingest-management-permission request-context :update)
        (index-svc/update-indexes request-context)
        {:status 200})

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; add routes for managing jobs
      (common-routes/job-api-routes
        (routes
          (POST "/refresh-collection-granule-aggregate-cache" {:keys [request-context]}
            (acl/verify-ingest-management-permission request-context :update)
            (cgac/refresh-cache request-context)
            {:status 200})))

      (POST "/reindex-provider-collections" {:keys [request-context params headers body]}
        (acl/verify-ingest-management-permission request-context :update)
        (index-svc/reindex-provider-collections request-context body)
        {:status 200})

      (POST "/reindex-tags" {:keys [request-context params headers]}
        (acl/verify-ingest-management-permission request-context :update)
        (index-svc/reindex-tags request-context)
        {:status 200})

      ;; Unindex all concepts within a provider
      (context "/provider/:provider-id" [provider-id]
        (DELETE "/" {:keys [request-context params headers]}
          (acl/verify-ingest-management-permission request-context :update)
          (index-svc/delete-provider request-context provider-id)
          {:status 200}))

      ;; Unindex a concept
      (context "/:concept-id/:revision-id" [concept-id revision-id]
        (DELETE "/" {:keys [request-context params headers]}
          (let [options {:ignore_conflict? (ignore-conflict? params)}]
            (index-svc/delete-concept
              request-context concept-id revision-id (assoc options :all-revisions-index? true))
            (index-svc/delete-concept
              request-context concept-id revision-id (assoc options :all-revisions-index? false))
            {:status 204})))

      (common-routes/health-api-routes index-svc/health))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      errors/invalid-url-encoding-handler
      errors/exception-handler
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      handler/site
      common-routes/pretty-print-response-handler
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



