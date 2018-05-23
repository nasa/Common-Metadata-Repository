(ns cmr.opendap.app.routes.rest.v2
  "This namespace defines the Version 2 REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.opendap.components.config :as config]
   [cmr.opendap.app.handler.cache :as cache-handler]
   [cmr.opendap.app.handler.collection :as collection-handler]
   [cmr.opendap.app.handler.core :as core-handler]
   [cmr.opendap.app.routes.rest.v1 :as routes-v1]
   [cmr.opendap.health :as health]
   [cmr.opendap.site.pages :as pages]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   REST API Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin-api
  [httpd-component]
  (concat
    [["/opendap/cache" {
      :get {:handler (cache-handler/lookup-all httpd-component)
            :roles #{:admin}}
      :delete {:handler (cache-handler/evict-all httpd-component)
               :roles #{:admin}}}]
     ["/opendap/cache/:item-key" {
      :get {:handler (cache-handler/lookup httpd-component)
            :roles #{:admin}}
      :delete {:handler (cache-handler/evict httpd-component)
               :roles #{:admin}}}]]
   (routes-v1/admin-api httpd-component)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all
  [httpd-component]
  (concat
   (routes-v1/ous-api httpd-component)
   (admin-api httpd-component)
   routes-v1/testing))
