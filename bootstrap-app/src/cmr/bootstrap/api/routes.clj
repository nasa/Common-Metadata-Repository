(ns cmr.bootstrap.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.api :as api]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.util :as util]
            [cmr.common.jobs :as jobs]
            [cmr.common.services.errors :as srv-errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.bootstrap.services.bootstrap-service :as bs]
            [cmr.bootstrap.services.health-service :as hs]
            [cmr.common.date-time-parser :as date-time-parser]))

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

      (context "/jobs" []
        ;; pause all jobs
        (POST "/pause" {:keys [request-context params headers]}
          (jobs/pause-jobs (get-in request-context [:system :scheduler]))
          {:status 204})

        ;; resume all jobs
        (POST "/resume" {:keys [request-context params headers]}
          (jobs/resume-jobs (get-in request-context [:system :scheduler]))
          {:status 204}))

      (context "/bulk_index" []
        (POST "/providers" {:keys [request-context body params]}
          (bulk-index-provider request-context body params))

        (POST "/collections" {:keys [request-context body params]}
          (bulk-index-collection request-context body params)))

      (GET "/health" {request-context :request-context :as request}
        (let [pretty? (api/pretty-request? request)
              {:keys [ok? dependencies]} (hs/health request-context)]
          {:status (if ok? 200 503)
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (json/generate-string dependencies {:pretty pretty?})})))))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



