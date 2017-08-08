(ns cmr.bootstrap.api.virtual-products
  "Defines the bulk virtual products functions for the bootstrap API."
  (:require
   [cmr.bootstrap.api.messages :as msg]
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.services.bootstrap-service :as service]
   [cmr.common.log :refer [info]]
   [cmr.common.services.errors :as errors]
   [cmr.virtual-product.data.source-to-virtual-mapping :as svm]))

(defn- validate-virtual-products-request
  "Throws an error if the virutal products request is invalid."
  [provider-id entry-title]
  (when-not (and provider-id entry-title)
    (errors/throw-service-error
      :bad-request
      (msg/required-params :provider-id :entry-title)))
  (when-not (svm/source-to-virtual-product-mapping
              [(svm/provider-alias->provider-id provider-id) entry-title])
    (errors/throw-service-error
      :not-found
      (msg/no-virtual-product-config provider-id entry-title))))

(defn bootstrap
  "Bootstrap virtual products."
  [context params]
  (let [dispatcher (api-util/get-dispatcher context params :bootstrap-virtual-products)
        {:keys [provider-id entry-title]} params]
    (validate-virtual-products-request provider-id entry-title)
    (info (msg/bootstrapping-virtual-products provider-id entry-title))
    (service/bootstrap-virtual-products context dispatcher provider-id entry-title)
    {:status 202 :body {:message (msg/bootstrapping-virtual-products)}}))
