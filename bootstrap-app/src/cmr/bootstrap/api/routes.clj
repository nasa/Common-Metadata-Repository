(ns cmr.bootstrap.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [cmr.acl.core :as acl]
   [cmr.bootstrap.api.bulk-index :as bulk-index]
   [cmr.bootstrap.api.bulk-migration :as bulk-migration]
   [cmr.bootstrap.api.fingerprint :as fingerprint]
   [cmr.bootstrap.api.rebalancing :as rebalancing]
   [cmr.bootstrap.api.resharding :as resharding]
   [cmr.bootstrap.api.virtual-products :as virtual-products]
   [cmr.bootstrap.data.metadata-retrieval.collection-metadata-cache :as cmc]
   [cmr.bootstrap.services.health-service :as hs]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.request-logger :as req-log]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.data.collections-for-gran-acls-by-concept-id-cache :as coll-for-gran-acls-caches]
   [cmr.common-app.data.humanizer-alias-cache :as humanizer-alias-cache]
   [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as mc]
   [cmr.common-app.services.kms-fetcher :as kms-fetcher]
   [cmr.common-app.services.provider-cache :as provider-cache]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as errors]
   [cmr.common.generics :as common-generic]
   [cmr.common.log :refer [info]]
   [cmr.elastic-utils.search.es-index-name-cache :as elastic-search-index-names-cache]
   [cmr.search.data.granule-counts-cache :as granule-counts-cache]
   [cmr.search.services.query-execution.has-granules-or-cwic-results-feature
    :as has-granules-or-cwic-results-feature]
   [compojure.core :refer [context DELETE GET POST routes]]
   [compojure.route :as route]
   [drift.core]
   [drift.execute]
   [inflections.core :as inf]
   [ring.middleware.json :as ring-json]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params])
  (:import
   [java.io File]))

(defn- build-routes [system]
  (routes
   (context (:relative-root-url system) []

     ;; for NGAP deployment health check
     (GET "/" {} {:status 200})
     (context "/bulk_migration" []
       (POST "/providers" {:keys [request-context body params]}
         (bulk-migration/migrate-provider request-context body params))
       (POST "/collections" {:keys [request-context body params]}
         (bulk-migration/migrate-collection request-context body params)))

     ;; Bulk Indexing Routes
     (context "/bulk_index" []
       (POST "/providers" {:keys [request-context body params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-provider request-context body params))
       (POST "/providers/all" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-all-providers request-context params))
       (POST "/collections" {:keys [request-context body params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-collection request-context body params))
       (POST "/after_date_time" {:keys [request-context body params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/data-later-than-date-time request-context body params))
       (POST "/system_concepts" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-system-concepts request-context params))
       (POST "/variables" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-variables request-context params))
       (POST "/variables/:provider-id" [provider-id :as {:keys [request-context params]}]
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-variables request-context params provider-id))
       (POST "/services" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-services request-context params))
       (POST "/services/:provider-id" [provider-id :as {:keys [request-context params]}]
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-services request-context params provider-id))
       (POST "/tools" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-tools request-context params))
       (POST "/tools/:provider-id" [provider-id :as {:keys [request-context params]}]
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-tools request-context params provider-id))
       (POST "/subscriptions" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-subscriptions request-context params))
       (POST "/subscriptions/:provider-id" [provider-id :as {:keys [request-context params]}]
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-subscriptions request-context params provider-id))
       (POST "/concepts" {:keys [request-context body params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/index-concepts-by-id request-context body params))
       (DELETE "/concepts" {:keys [request-context body params]}
         (acl/verify-ingest-management-permission request-context :update)
         (bulk-index/delete-concepts-by-id request-context body params))
       ;; generating pluralized endpoints for each generic document type & converting to singular in call
       (context ["/:concept-type" :concept-type
                 (re-pattern common-generic/plural-generic-concept-types-reg-ex)] [concept-type]
         (POST "/" {:keys [request-context params]}
           (acl/verify-ingest-management-permission request-context :update)
           (bulk-index/index-generics request-context params (inf/singular concept-type)))
         (POST "/:provider-id" [provider-id :as {:keys [request-context params]}]
           (acl/verify-ingest-management-permission request-context :update)
           (bulk-index/index-generics request-context params (inf/singular concept-type) provider-id))))

     ;; Routes for rebalancing
     (context "/rebalancing_collections/:concept-id" [concept-id]
       ;; Start rebalancing
       (POST "/start" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (rebalancing/start-collection request-context concept-id params))
       ;; Get counts of rebalancing data
       (GET "/status" {:keys [request-context]}
         (acl/verify-ingest-management-permission request-context :update)
         (rebalancing/get-status request-context concept-id))
       ;; Complete reindexing
       (POST "/finalize" {:keys [request-context]}
         (acl/verify-ingest-management-permission request-context :update)
         (rebalancing/finalize-collection request-context concept-id)))

     ;; Resharding routes
     (context "/reshard/:index" [index]
       (POST "/start" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (resharding/start request-context index params))
       (POST "/finalize" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (resharding/finalize request-context index params))
       (GET "/status" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (resharding/get-status request-context index params))
       (POST "/rollback" {:keys [request-context params]}
         (acl/verify-ingest-management-permission request-context :update)
         (resharding/rollback request-context index params)))

     ;; Virtual Products Routes
     (context "/virtual_products" []
       (POST "/" {:keys [request-context params]}
         (virtual-products/bootstrap request-context params)))

     ;; Fingerprinting Routes
     (context "/fingerprint" []
       (POST "/variables" {:keys [request-context body params]}
         (acl/verify-ingest-management-permission request-context :update)
         (fingerprint/fingerprint-variables request-context body params))
       (POST "/variables/:concept-id" [concept-id :as {:keys [request-context]}]
         (acl/verify-ingest-management-permission request-context :update)
         (fingerprint/fingerprint-by-id request-context concept-id)))

     ;; Add routes for accessing caches
     common-routes/cache-api-routes
     (context "/caches/refresh/:cache-name" [cache-name]
       (POST "/" {:keys [request-context]}
         (acl/verify-ingest-management-permission request-context :update)
         (let [keyword-cache-name (keyword cache-name)
               refresh-result
               (cond
                 (= keyword-cache-name mc/cache-key)
                 (cmc/refresh-cache request-context)

                 (= keyword-cache-name kms-fetcher/kms-cache-key)
                 (kms-fetcher/refresh-kms-cache request-context)

                 (= keyword-cache-name provider-cache/cache-key)
                 (provider-cache/refresh-provider-cache request-context)

                 (= keyword-cache-name coll-for-gran-acls-caches/coll-by-concept-id-cache-key)
                 (coll-for-gran-acls-caches/refresh-entire-cache request-context)

                 (= keyword-cache-name elastic-search-index-names-cache/index-names-cache-key)
                 (elastic-search-index-names-cache/refresh-index-names-cache request-context)

                 (= keyword-cache-name humanizer-alias-cache/humanizer-alias-cache-key)
                 (humanizer-alias-cache/refresh-entire-cache request-context)

                 (= keyword-cache-name has-granules-or-cwic-results-feature/has-granules-or-cwic-cache-key)
                 (has-granules-or-cwic-results-feature/refresh-has-granules-or-cwic-map request-context)

                 (= keyword-cache-name has-granules-or-cwic-results-feature/has-granules-or-opensearch-cache-key)
                 (has-granules-or-cwic-results-feature/refresh-has-granules-or-opensearch-map request-context)

                 (= keyword-cache-name granule-counts-cache/granule-counts-cache-key)
                 (granule-counts-cache/refresh-granule-counts-cache request-context)

                 :else :not-found)]
           (if (= refresh-result :not-found)
             (route/not-found "Not Found")
             {:status 200}))))

     ;; db migration route
     (POST "/db-migrate" {:keys [request-context params]}
       (acl/verify-ingest-management-permission request-context :update)
       (let [migrate-args (if-let [version (:version params)]
                            ["-c" "config.bootstrap-migrate-config/app-migrate-config" "-version" version]
                            ["-c" "config.bootstrap-migrate-config/app-migrate-config"])]
         (info "Running db migration with args:" migrate-args)
         ;; drift looks for migration files within the user.directory, which is /app in service envs.
         ;; Dev dockerfile manually creates /app/cmr-files to store the unzipped cmr jar so that drift
         ;; can find the migration files correctly
         ;; we had to force method change in drift to set the correct path
         (try
           ;; trying non-local path to find drift migration files
           (with-redefs [drift.core/user-directory (fn [] (new File (str (.getProperty (System/getProperties) "user.dir") "/drift-migration-files")))]
             (drift.execute/run migrate-args))
           (catch Exception _e
             (try
               (println "Caught exception trying to find migration files for cloud env. We are probably in local env. Trying local route to migration files...")
               (with-redefs [drift.core/user-directory (fn [] (new File (str (.getProperty (System/getProperties) "user.dir") "/checkouts/bootstrap-app/src")))]
                 (drift.execute/run migrate-args))
               (catch Exception _e2
                 (println "Caught exception trying to find migration files with local route external, trying last resort migration local :in-memory")
                 (drift.execute/run (cons migrate-args "migrate")))))))
       {:status 204})

     ;; Add routes for checking health of the application
     (common-health/health-api-routes hs/health))
   (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      errors/invalid-url-encoding-handler
      errors/exception-handler
      common-routes/add-request-id-response-handler
      req-log/log-ring-request ;; Must be after request id
      (context/build-request-context-handler system)
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      ring-json/wrap-json-body
      common-routes/pretty-print-response-handler
      params/wrap-params
      ;; Last in line, but really first for request as they process in reverse
      req-log/add-time-stamp))
