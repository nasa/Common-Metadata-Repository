(ns cmr.bootstrap.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
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

(defn- db-synchronize
  "Synchronizes Catalog REST and Metadata DB looking for differences that were ingested between
  start date and end date"
  [context params]
  (let [{:keys [synchronous start_date end_date]} params]
    (when-not (and start_date end_date)
      (srv-errors/throw-service-error :bad-request "start_date and end_date are required parameters"))
    (bs/db-synchronize context synchronous
                       (date-time-parser/parse-datetime start_date)
                       (date-time-parser/parse-datetime end_date))
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

      (context "/bulk_index" []
        (POST "/providers" {:keys [request-context body params]}
          (bulk-index-provider request-context body params))

        (POST "/collections" {:keys [request-context body params]}
          (bulk-index-collection request-context body params)))

      (GET "/health" {request-context :request-context params :params}
        (let [{pretty? :pretty} params
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



