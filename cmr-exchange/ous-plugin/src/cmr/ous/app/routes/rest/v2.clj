(ns cmr.ous.app.routes.rest.v2
  "This namespace defines the Version 2 REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.ous.app.handler.auth-cache :as auth-cache-handler]
   [cmr.ous.app.handler.collection :as collection-handler]
   [cmr.ous.app.handler.concept-cache :as concept-cache-handler]
   [cmr.ous.app.routes.rest.v1 :as routes-v1]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   REST API Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin-api
  [httpd-component]
  (concat
    [;; Authz cache
     ["/service-bridge/cache/auth" {
      :get {:handler (auth-cache-handler/lookup-all httpd-component)
            :roles #{:admin}}
      :delete {:handler (auth-cache-handler/evict-all httpd-component)
               :roles #{:admin}}}]
     ["/service-bridge/cache/auth/:item-key" {
      :get {:handler (auth-cache-handler/lookup httpd-component)
            :roles #{:admin}}
      :delete {:handler (auth-cache-handler/evict httpd-component)
               :roles #{:admin}}}]
     ;; Concept cache
     ["/service-bridge/cache/concept" {
      :get {:handler (concept-cache-handler/lookup-all httpd-component)
            :roles #{:admin}}
      :delete {:handler (concept-cache-handler/evict-all httpd-component)
               :roles #{:admin}}}]
     ["/service-bridge/cache/concept/:item-key" {
      :get {:handler (concept-cache-handler/lookup httpd-component)
            :roles #{:admin}}
      :delete {:handler (concept-cache-handler/evict httpd-component)
               :roles #{:admin}}}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all
  [httpd-component]
  (concat
   (routes-v1/ous-api httpd-component)
   (admin-api httpd-component)))
