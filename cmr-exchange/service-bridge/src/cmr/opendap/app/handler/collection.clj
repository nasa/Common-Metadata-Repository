(ns cmr.opendap.app.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [cmr.authz.token :as token]
   [cmr.exchange.query.core :as base-query]
   [cmr.exchange.query.core :as query]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.opendap.components.config :as config]
   [cmr.ous.util.http.request :as ous-reqeust]
   [org.httpkit.server :as server]
   [org.httpkit.timer :as timer]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Service Bridge Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-service-endpoint
  [component destination]
  (case destination
    :cmr (config/get-cmr-search-endpoint component)
    :giovanni (config/get-giovanni-endpoint component)
    :edsc (config/get-edsc-endpoint component)))

(defn params->service-url
  [component destination data]
  (let [service-endpoint (get-service-endpoint component destination)]
    (format "%s?%s" service-endpoint
                    (-> data
                        (query/parse {:destination destination})
                        base-query/->query-string))))

(defn bridge-services
  "Here's what we expect a request might look like:
  https://this-service/service-bridge/collection/c123?
    destination=giovanni&
    bounding-box=[...]&
    temporal=[...]&
    variables=V123,V234,V345&
    granules=G123"
  [component]
  (fn [request]
    (let [user-token (token/extract request)
          ;; TODO: Destination will not be found in path-params.
          ;; We need to retrieve this from the raw-params.
          {:keys [concept-id destination]} (:path-params request)
          api-version (ous-reqeust/accept-api-version component request)
          data (-> request
                   :params
                   (merge {:collection-id concept-id}))]
      (log/warnf "Handling bridge request from %s ..." (:referer request))
      (log/warnf "Bridging service to %s ..." destination)
      (response/json {
                      :service {
                                :url (params->service-url component destination data)}}))))
