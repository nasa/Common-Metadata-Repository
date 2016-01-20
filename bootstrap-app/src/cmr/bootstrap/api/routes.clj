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
            [cmr.common.api.context :as context]
            [cmr.bootstrap.services.bootstrap-service :as bs]
            [cmr.bootstrap.services.health-service :as hs]
            [cmr.common.date-time-parser :as date-time-parser]
            [cmr.common-app.api.routes :as common-routes]
            [cmr.virtual-product.source-to-virtual-mapping :as svm]))

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
  (let [{:keys [provider-id entry-title synchronous]} params]
    (when-not (and provider-id entry-title)
      (srv-errors/throw-service-error
        :bad-request
        "provider-id and entry-title are required parameters."))
    (when-not (svm/source-to-virtual-product-mapping
                [(svm/provider-alias->provider-id provider-id) entry-title])
      (srv-errors/throw-service-error
        :not-found
        (format "No virtual product configuration found for provider [%s] and entry-title [%s]"
                provider-id
                entry-title)))
    (info (format "Bootstrapping virtual products for provider [%s] entry-title [%s]"
                  provider-id
                  entry-title))
    (bs/bootstrap-virtual-products context (= "true" synchronous) provider-id entry-title)
    {:status 202 :body {:message "Bootstrapping virtual products."}}))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/bulk_migration" []
        (POST "/providers" {:keys [request-context body params]}
          (migrate-provider request-context body params))
        (POST "/collections" {:keys [request-context body params]}
          (migrate-collection request-context body params)))

      (context "/bulk_index" []
        (POST "/providers" {:keys [request-context body params]}
          (bulk-index-provider request-context body params))

        (POST "/collections" {:keys [request-context body params]}
          (bulk-index-collection request-context body params)))

      (context "/virtual_products" []
        (POST "/" {:keys [request-context params]}
          (bootstrap-virtual-products request-context params)))

      ;; Add routes for accessing caches
      common-routes/cache-api-routes

      ;; Add routes for checking health of the application
      (common-routes/health-api-routes hs/health))))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      errors/invalid-url-encoding-handler
      errors/exception-handler
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      ring-json/wrap-json-body
      common-routes/pretty-print-response-handler
      params/wrap-params))
