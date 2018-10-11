(ns cmr.opendap.app.routes.rest.v1
  "This namespace defines the Version 1 REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.opendap.app.handler.collection :as collection-handler]
   [cmr.opendap.app.handler.core :as core-handler]
   [cmr.http.kit.app.handler :as base-handler]
   [cmr.opendap.health :as health]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   REST API Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin-api
  [httpd-component]
  [["/service-bridge/health" {
    :get (core-handler/health httpd-component)
    :options base-handler/ok}]
   ["/service-bridge/ping" {
    :get {:handler core-handler/ping
          :roles #{:admin}}
    :post {:handler core-handler/ping
           :roles #{:admin}}
    :options base-handler/ok}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Testing Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def testing
  [["/testing/401" {:get (base-handler/status :unauthorized)}]
   ["/testing/403" {:get (base-handler/status :forbidden)}]
   ["/testing/404" {:get (base-handler/status :not-found)}]
   ["/testing/405" {:get (base-handler/status :method-not-allowed)}]
   ["/testing/500" {:get (base-handler/status :internal-server-error)}]
   ["/testing/503" {:get (base-handler/status :service-unavailable)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all
  [httpd-component]
  (concat
   (admin-api httpd-component)
   testing))
