(ns cmr.bootstrap.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [ring.middleware.params :as params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.keyword-params :as keyword-params]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.util :as util]
            [cmr.common.jobs :as jobs]
            [cmr.common.services.errors :as srv-errors]
            [cmr.acl.core :as acl]
            [cmr.system-trace.http :as http-trace]
            [cmr.bootstrap.services.bootstrap-service :as bs]
            [cmr.bootstrap.services.health-service :as hs]
            [cmr.common.date-time-parser :as date-time-parser]
            [cmr.common-app.api.routes :as common-routes]
            [cmr.virtual-product.config :as vp-config]))

(defn- migrate-collection
  "Copy collections data from catalog-rest to metadata db (including granules)"
  [context provider-id-collection-map params]
  (let [provider-id (get provider-id-collection-map "provider_id")
        synchronous (:synchronous params)
        collection-id (get provider-id-collection-map "collection_id")]
    (bs/migrate-collection context provider-id collection-id synchronous)
    {:status 202
     :body {:message (str "Processing collection " collection-id "for provider " provider-id)}}))

(defn- migrate-provider
  "Copy a single provider's data from catalog-rest to metadata db (including collections and granules)"
  [context provider-id-map params]
  (let [provider-id (get provider-id-map "provider_id")
        synchronous (:synchronous params)]
    (bs/migrate-provider context provider-id synchronous)
    {:status 202 :body {:message (str "Processing provider " provider-id)}}))

(def valid-sync-types
  "This is the list of valid sync types that can be specified for a database synchronization."
  #{"missing" "updates" "deletes"})

(def default-sync-types
  #{"updates" "deletes"})

(defn- db-synchronize
  "Synchronizes Catalog REST and Metadata DB looking for differences that were ingested between
  start date and end date"
  [context params]
  (let [{:keys [synchronous start_date end_date provider_id entry_title sync_types]} params
        sync_types (cond
                     (nil? sync_types) default-sync-types
                     (not (sequential? sync_types)) #{sync_types}
                     :else (set sync_types))]

    ;; Verify entry title and provider id are given together
    (when (and entry_title (not provider_id))
      (srv-errors/throw-service-error :bad-request "If entry_title is provided provider_id must be provided as well."))

    ;; Verify sync types are valid
    (when (and sync_types (not (every? valid-sync-types sync_types)))
      (srv-errors/throw-service-error
        :bad-request (format "The sync_types %s are not supported. Valid values are: %s"
                             (pr-str sync_types) (pr-str valid-sync-types))))

    ;; Verify only one of missing or updates is used as sync types but not both
    (when (and (sync_types "updates") (sync_types "missing"))
      (srv-errors/throw-service-error
        :bad-request "Only one of the sync_types [updates] and [missing] can be provided but not both"))

    ;; Verify start date and end data are only used if updates sync is being done
    (when (and (not (sync_types "updates")) (or start_date end_date))
      (srv-errors/throw-service-error
        :bad-request
        "start_date and/or end_date were provided. These only apply if the [updates] sync type is used."))

    (bs/db-synchronize context synchronous
                       (util/remove-nil-keys
                         {:start-date (date-time-parser/parse-datetime (or start_date "1970-01-01T00:00:00Z"))
                          :end-date (date-time-parser/parse-datetime (or end_date "2100-01-01T00:00:00Z"))
                          :provider-id provider_id
                          :sync-types (map keyword sync_types)
                          :entry-title entry_title}))
    {:status 202 :body {:message "Synchronizing databases."}}))

(defn- bulk-index-provider
  "Index all the collections and granules for a given provider."
  [context provider-id-map params]
  (let [provider-id (get provider-id-map "provider_id")
        synchronous (:synchronous params)
        start-index (Long/parseLong (get params :start_index "0"))
        result (bs/index-provider context provider-id synchronous start-index)
        msg (if synchronous
              result
              (str "Processing provider " provider-id " for bulk indexing from start index " start-index))]
    {:status 202
     :body {:message msg}}))

(defn- bulk-index-collection
  "Index all the granules in a collection"
  [context provider-id-collection-map params]
  (let [provider-id (get provider-id-collection-map "provider_id")
        collection-id (get provider-id-collection-map "collection_id")
        synchronous (:synchronous params)
        result (bs/index-collection context provider-id collection-id synchronous)
        msg (if synchronous
              result
              (str "Processing collection " collection-id " for bulk indexing."))]
    {:status 202
     :body {:message msg}}))

(defn- bootstrap-virtual-products
  "Bootstrap virtual products."
  [context params]
  (let [{:keys [provider-id entry-title]} params]
    (when-not (and provider-id entry-title)
      (srv-errors/throw-service-error
        :bad-request
        "Provider_id and entry_title are required parameters."))
    (when-not (vp-config/source-to-virtual-product-config [provider-id entry-title])
      (srv-errors/throw-service-error
        :not-found
        (format "No virtual product configuration found for provider [%s] and entry-title [%s]"
                provider-id
                entry-title)))
    (info "Bootstrapping virtual products for provider [" provider-id
          "] entry-title [" entry-title)
    (bs/bootstrap-virtual-products context (= "true" (:synchronous params)) provider-id entry-title)
    {:status 202 :body {:message "Bootstrapping virtual products."}}))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/bulk_migration" []
        (POST "/providers" {:keys [request-context body params]}
          (migrate-provider request-context body params))
        (POST "/collections" {:keys [request-context body params]}
          (migrate-collection request-context body params)))

      (context "/db_synchronize" []
        (POST "/" {:keys [request-context params]}
          (db-synchronize request-context params)))

      (context "/bulk_index" []
        (POST "/providers" {:keys [request-context body params]}
          (bulk-index-provider request-context body params))

        (POST "/collections" {:keys [request-context body params]}
          (bulk-index-collection request-context body params)))

      (context "/virtual_products" []
        (POST "/" {:keys [request-context params]}
          (bootstrap-virtual-products request-context params)))

      ;; Add routes for managing jobs.
      (common-routes/job-api-routes)

      ;; Add routes for accessing caches
      common-routes/cache-api-routes

      ;; Add routes for checking health of the application
      (common-routes/health-api-routes hs/health))))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      (http-trace/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      ring-json/wrap-json-body
      common-routes/pretty-print-response-handler
      params/wrap-params))
