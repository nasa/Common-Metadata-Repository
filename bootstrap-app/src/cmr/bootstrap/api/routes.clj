(ns cmr.bootstrap.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [cmr.acl.core :as acl]
   [cmr.bootstrap.services.bootstrap-service :as bs]
   [cmr.bootstrap.services.health-service :as hs]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as errors]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :refer [info]]
   [cmr.common.services.errors :as srv-errors]
   [cmr.common.util :as util]
   [cmr.virtual-product.data.source-to-virtual-mapping :as svm]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [drift.execute :as drift]
   [ring.middleware.json :as ring-json]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn- synchronous?
  "Returns true if the params contains the :synchronous key and it's
  value converted to lower case equals the string 'true'."
  [params]
  (= "true" (when (:synchronous params) (str/lower-case (:synchronous params)))))

(defn- get-dispatcher
  "Returns the correct dispatcher to use based on the system configuration and the request."
  [context params request-type]
  (if (synchronous? params)
    (get-in context [:system :synchronous-dispatcher])
    (let [dispatcher-type (get bs/request-type->dispatcher request-type :core-async-dispatcher)]
      (get-in context [:system dispatcher-type]))))

(defn- migrate-collection
  "Copy collections data from catalog-rest to metadata db (including granules)"
  [context provider-id-collection-map params]
  (let [dispatcher (get-dispatcher context params :migrate-collection)
        provider-id (get provider-id-collection-map "provider_id")
        collection-id (get provider-id-collection-map "collection_id")]
    (bs/migrate-collection context dispatcher provider-id collection-id)
    {:status 202
     :body {:message (str "Processing collection " collection-id "for provider " provider-id)}}))

(defn- migrate-provider
  "Copy a single provider's data from catalog-rest to metadata db (including collections and
  granules)."
  [context provider-id-map params]
  (let [dispatcher (get-dispatcher context params :migrate-provider)
        provider-id (get provider-id-map "provider_id")]
    (bs/migrate-provider context dispatcher provider-id)
    {:status 202 :body {:message (str "Processing provider " provider-id)}}))

(defn- bulk-index-provider
  "Index all the collections and granules for a given provider."
  [context provider-id-map params]
  (let [dispatcher (get-dispatcher context params :index-provider)
        provider-id (get provider-id-map "provider_id")
        start-index (Long/parseLong (get params :start_index "0"))
        result (bs/index-provider context dispatcher provider-id start-index)
        msg (if
              result
              (str "Processing provider " provider-id " for bulk indexing from start index "
                   start-index))]
    {:status 202
     :body {:message msg}}))

(defn- bulk-index-data-later-than-date-time
  "Index all the data with a revision-date later than a given date-time."
  [context params]
  (let [dispatcher (get-dispatcher context params :index-data-later-than-date-time)
        date-time (:date_time params)]
    (if-let [date-time-value (date-time-parser/try-parse-datetime date-time)]
      (let [result (bs/index-data-later-than-date-time context dispatcher date-time-value)
            msg (if
                  (:message result)
                  (str "Processing data after " date-time " for bulk indexing"))]
        {:status 202
         :body {:message msg}})
      ;; Can't parse date-time.
      (srv-errors/throw-service-error :invalid-data (str date-time " is not a valid date-time.")))))

(defn- bulk-index-collection
  "Index all the granules in a collection"
  [context provider-id-collection-map params]
  (let [dispatcher (get-dispatcher context params :index-collection)
        provider-id (get provider-id-collection-map "provider_id")
        collection-id (get provider-id-collection-map "collection_id")
        result (bs/index-collection context dispatcher provider-id collection-id)
        msg (if
              result
              (str "Processing collection " collection-id " for bulk indexing."))]
    {:status 202
     :body {:message msg}}))

(defn- bulk-index-system-concepts
  "Index all tags, acls, and access-groups."
  [context params]
  (let [dispatcher (get-dispatcher context params :index-system-concepts)
        start-index (or (:start-index params) 0)
        result (bs/index-system-concepts context dispatcher start-index)
        msg (if (synchronous? params)
              (str "Processed " result " system concepts for bulk indexing.")
              (str "Processing system concepts for bulk indexing."))]
    {:status 202
     :body {:message msg}}))

(defn bulk-index-concepts-by-id
  "Bulk index concepts of the given type for the given provider-id with the given concept-ids.
  The request-details-map should contain the following:
    provider-id  - \"the id of the provider for all the concepts\"
    concept_type - the concept type for all the concepts, e.g., \"granule\", \"collection\", etc.
    concept_ids  - a vector of concept ids."
  [context request-details-map params]
  (let [dispatcher (get-dispatcher context params :index-concepts-by-id)
        provider-id (get request-details-map "provider_id")
        concept-type (keyword (get request-details-map "concept_type"))
        concept-ids (get request-details-map "concept_ids")
        result (bs/index-concepts-by-id context dispatcher provider-id concept-type concept-ids)
        msg (if (synchronous? params)
              (str "Processed " result " concepts for bulk indexing.")
              (str "Processing concepts for bulk indexing."))]
    {:status 202
     :body {:message msg}}))

(defn bulk-delete-concepts-from-index-by-id
  "Delete concepts from the indexes by concept-id."
  [context request-details-map params]
  (let [dispatcher (get-dispatcher context params :delete-concepts-from-index-by-id)
        provider-id (get request-details-map "provider_id")
        concept-type (keyword (get request-details-map "concept_type"))
        concept-ids (get request-details-map "concept_ids")
        result (bs/delete-concepts-from-index-by-id context dispatcher provider-id concept-type
                                                    concept-ids)
        msg (if (synchronous? params)
              (str "Processed " result "conccepts for bulk deletion from indexes.")
              (str "Processing concepts for bulk deletion from indexes."))]
    {:status 202
     :body {:message msg}}))

(defn- validate-virtual-products-request
  "Throws an error if the virutal products request is invalid."
  [provider-id entry-title]
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
              entry-title))))

(defn- bootstrap-virtual-products
  "Bootstrap virtual products."
  [context params]
  (let [dispatcher (get-dispatcher context params :bootstrap-virtual-products)
        {:keys [provider-id entry-title]} params]
    (validate-virtual-products-request provider-id entry-title)
    (info (format "Bootstrapping virtual products for provider [%s] entry-title [%s]"
                  provider-id
                  entry-title))
    (bs/bootstrap-virtual-products context dispatcher provider-id entry-title)
    {:status 202 :body {:message "Bootstrapping virtual products."}}))

(defn start-rebalance-collection
  "Kicks off rebalancing the granules in the collection into their own index."
  [context concept-id params]
  (let [dispatcher (get-dispatcher context params :index-collection)]
    (bs/start-rebalance-collection context dispatcher concept-id)
    {:status 200
     :body {:message (str "Rebalancing started for collection " concept-id)}}))

(defn rebalance-status
  "Gets the status of rebalancing a collection."
  [context concept-id]
  {:status 200
   :body (bs/rebalance-status context concept-id)})

(defn finalize-rebalance-collection
  "Completes rebalancing the granules in the collection"
  [context concept-id]
  (bs/finalize-rebalance-collection context concept-id)
  {:status 200
   :body {:message (str "Rebalancing completed for collection " concept-id)}})

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      ;; for NGAP deployment health check
      (GET "/" {} {:status 200})
      (context "/bulk_migration" []
        (POST "/providers" {:keys [request-context body params]}
          (migrate-provider request-context body params))
        (POST "/collections" {:keys [request-context body params]}
          (migrate-collection request-context body params)))

      (context "/bulk_index" []
        (POST "/providers" {:keys [request-context body params]}
          (bulk-index-provider request-context body params))

        (POST "/collections" {:keys [request-context body params]}
          (bulk-index-collection request-context body params))

        (POST "/after_date_time" {:keys [request-context params]}
          (bulk-index-data-later-than-date-time request-context params))

        (POST "/system_concepts" {:keys [request-context params]}
          (bulk-index-system-concepts request-context params))

        (POST "/concepts" {:keys [request-context body params]}
          (bulk-index-concepts-by-id request-context body params))

        (DELETE "/concepts" {:keys [request-context body params]}
          (bulk-delete-concepts-from-index-by-id request-context body params)))

      (context "/rebalancing_collections/:concept-id" [concept-id]
        ;; Start rebalancing
        (POST "/start" {:keys [request-context params]}
          (start-rebalance-collection request-context concept-id params))

        ;; Get counts of rebalancing data
        (GET "/status" {:keys [request-context]}
          (rebalance-status request-context concept-id))

        ;; Complete reindexing
        (POST "/finalize" {:keys [request-context]}
          (finalize-rebalance-collection request-context concept-id)))

      (context "/virtual_products" []
        (POST "/" {:keys [request-context params]}
          (bootstrap-virtual-products request-context params)))

      ;; Add routes for accessing caches
      common-routes/cache-api-routes

      ;; db migration route
      (POST "/db-migrate" {:keys [request-context params]}
        (acl/verify-ingest-management-permission request-context :update)
        (let [migrate-args (if-let [version (:version params)]
                             ["migrate" "-version" version]
                             ["migrate"])]
          (info "Running db migration:" migrate-args)
          (drift/run (conj migrate-args "-c" "config.migrate-config/app-migrate-config")))
        {:status 204})

      ;; Add routes for checking health of the application
      (common-health/health-api-routes hs/health))))

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
