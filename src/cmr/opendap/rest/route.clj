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
      :post {
        :handler (collection-handler/batch-generate conn-mgr)
        ;; XXX protecting collections will be a little different than
        ;;     protecting a single collection, since the concept-id isn't in
        ;;     the path-params. Instead, we'll have to parse the body, extract
        ;;     the concepts ids from that, create an ACL query containing
        ;;     multiple concept ids, and then check those results.
        ;:permission #{...}
        }
      :options core-handler/ok}]
     ["/opendap/ous/collection/:concept-id" {
      :get {
        :handler (collection-handler/generate-urls conn-mgr)
        :permissions #{:read}}
      :post {
        :handler (collection-handler/generate-urls conn-mgr)
        :permissions #{:read}}
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
    :get (core-handler/health httpd-component)
    :options core-handler/ok}]
   ["/ping" {
    :get {
      :handler core-handler/ping
      :roles #{:admin}}
    :post {
      :handler core-handler/ping
      :roles #{:admin}}
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
