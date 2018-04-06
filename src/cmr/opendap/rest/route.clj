(ns cmr.opendap.rest.route
  "This namespace defines the REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.opendap.components.config :as config]
   [cmr.opendap.health :as health]
   [cmr.opendap.rest.handler.collection :as collection-handler]
   [cmr.opendap.rest.handler.core :as core-handler]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   CMR OPeNDAP Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ous
  [httpd-component]
  (let [conn-mgr :not-implemented]
    [["/opendap/ous/collections" {
      :post (collection-handler/batch-generate conn-mgr)
      :options core-handler/ok}]
     ["/opendap/ous/collection/:concept-id" {
      :get (collection-handler/generate-urls conn-mgr)
      :post (collection-handler/generate-urls conn-mgr)
      :options core-handler/ok}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Static Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn static
  [httpd-component]
  (let [docroot (config/http-docroot httpd-component)]
    [["/static/*" {
      :get (core-handler/static-files docroot)}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Admin Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin
  [httpd-component]
  [["/health" {
    :get {:handler (core-handler/health httpd-component)
          :roles #{:admin}}
    :options core-handler/ok}]
   ["/ping" {
    :get core-handler/ping
    :post core-handler/ping
    :options core-handler/ok}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Testing Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def testing
  [["/testing/401" {:get (core-handler/status :unauthorized)}]
   ["/testing/403" {:get (core-handler/status :forbidden)}]
   ["/testing/404" {:get (core-handler/status :not-found)}]
   ["/testing/405" {:get (core-handler/status :method-not-allowed)}]
   ["/testing/500" {:get (core-handler/status :internal-server-error)}]
   ["/testing/503" {:get (core-handler/status :service-unavailable)}]])
