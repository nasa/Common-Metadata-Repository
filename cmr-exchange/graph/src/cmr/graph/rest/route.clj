(ns cmr.graph.rest.route
  (:require
   [cmr.graph.components.config :as config]
   [cmr.graph.components.neo4j :as neo4j]
   [cmr.graph.health :as health]
   [cmr.graph.rest.handler :as handler]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   CMR Graph Database Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn collections
  [httpd-component]
  (let [conn (neo4j/get-conn httpd-component)]
    [["/collections"
      {:get (handler/get-collections conn)
       :delete (handler/delete-collections conn)
       :post (handler/add-collections conn)
       :options handler/ok}]
     ["/collections/import"
      {:post (handler/import-collection-data conn)
       :options handler/ok}]
     ["/collection"
      {:post (handler/add-collection conn)
       :options handler/ok}]
     ["/collection/:concept-id"
      {:get (handler/get-collection conn)
       :delete (handler/delete-collection conn)
       :put (handler/update-collection conn)
       :options handler/ok}]]))

(defn relationships
  [httpd-component]
  (let [conn (neo4j/get-conn httpd-component)]
    [["/relationships/related-urls/collections/:concept-id"
      {:get (handler/get-collections-via-related-urls conn)
       :options handler/ok}]
     ["/relationships/providers/related-urls/:provider-name"
       {:get (handler/get-urls-via-provider conn)
        :options handler/ok}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   CMR Elasticsearch Graph Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; TBD

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Demo Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn movie-demo
  [httpd-component]
  (let [conn (neo4j/get-conn httpd-component)]
    [["/demo/movie/graph/:limit" {
      :get (handler/movie-demo-graph conn)
      :options handler/ok}]
     ["/demo/movie/search" {
      :get (handler/movie-demo-search conn)
      :options handler/ok}]
     ["/demo/movie/title/:title" {
      :get (handler/movie-demo-title conn)
      :options handler/ok}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Static Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn static
  [httpd-component]
  (let [docroot (config/http-docroot httpd-component)]
    [["/static/*" {
      :get (handler/static-files docroot)}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Admin Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin
  [httpd-component]
  (let [conn (neo4j/get-conn httpd-component)]
    [["/health" {
                 :get (handler/health httpd-component)
                 :options handler/ok}]
     ["/reset" {
                :delete (handler/reset conn)
                :options handler/ok}]
     ["/reload" {:post (handler/reload conn)
                 :options handler/ok}]
     ["/ping" {
               :get handler/ping
               :post handler/ping
               :options handler/ok}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   DANGEROUS!!! REMOVE ME!!! *Injection Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn dangerous
  [httpd-component]
  (let [conn (neo4j/get-conn httpd-component)]
    [["/queries/cypher"
      {:get (handler/cypher-injection-get conn)
       :post (handler/cypher-injection-post conn)
       :options handler/ok}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; TBD
