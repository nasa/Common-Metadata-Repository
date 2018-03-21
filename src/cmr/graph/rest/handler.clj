(ns cmr.graph.rest.handler
  (:require
   [clojurewerkz.neocons.rest.cypher :as cypher]
   [clojusc.twig :as twig]
   [cmr.graph.collections.core :as collections]
   [cmr.graph.demo.movie :as movie]
   [cmr.graph.health :as health]
   [cmr.graph.rest.response :as response]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Graph Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-collections
  [conn]
  (fn [request]
    (->> conn
         (collections/get-all)
         (response/json request))))

(defn delete-collections
  [conn]
  (fn [request]
    (->> conn
         (collections/delete-all)
         (response/json request))))

(defn add-collections
  "Expects the body to be a JSON payload of an array of node objects."
  [conn]
  (fn [request]
    (->> request
         :body
         slurp
         ;(collections/batch-add conn)
         ((fn [_] {:error :not-implemented}))
         (response/json request))))

(defn add-collection
  "Expects the body to be a JSON payload of a node object."
  [conn]
  (fn [request]
    (->> request
         :body
         slurp
         ;(collections/add-collection conn)
         ((fn [_] {:error :not-implemented}))
         (response/json request))))

(defn get-collection
  [conn]
  (fn [request]
    (->> [:path-params :concept-id]
         (get-in request)
         ;(collections/get-collection conn)
         ((fn [_] {:error :not-implemented}))
         (response/json request))))

(defn delete-collection
  [conn]
  (fn [request]
    (->> [:path-params :concept-id]
         (get-in request)
         ;(collections/delete-collection conn)
         ((fn [_] {:error :not-implemented}))
         (response/json request))))

(defn update-collection
  [conn]
  (fn [request]
    (->> [:path-params :concept-id]
         (get-in request)
         ;(collections/update-collection conn)
         ((fn [_] {:error :not-implemented}))
         (response/json request))))

(defn- get-related-urls
  [conn concept-id]
  (let [result (collections/get-collections-via-related-urls conn concept-id)]
    (map #(get % "u.name") result)))

(defn get-collections-via-related-urls
  [conn]
  (fn [request]
    (let [related-urls (get-related-urls conn (get-in request [:path-params :concept-id]))
          result (collections/get-concept-ids-by-urls conn related-urls)
          concept-ids (distinct (map #(get % "c.conceptId") result))]
      (response/json request concept-ids))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Demo Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn movie-demo-graph
  [conn]
  (fn [request]
    (->> [:path-params :limit]
         (get-in request)
         Integer.
         (movie/get-graph conn)
         (response/json request))))

(defn movie-demo-search
  [conn]
  (fn [request]
    (->> [:params :q]
         (get-in request)
         (movie/search conn)
         (response/json request))))

(defn movie-demo-title
  [conn]
  (fn [request]
    (->> [:path-params :title]
         (get-in request)
         (codec/percent-decode)
         (movie/get-movie conn)
         (response/json request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Admin Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn health
  [component]
  (fn [request]
    (->> component
         health/components-ok?
         (response/json request))))

(def ping
  (fn [request]
    (response/json request {:result :pong})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   DANGEROUS!!! REMOVE ME!!! *Injection Handlers   ;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cypher-injection-get
  "Call with something like this:

  $ curl http://localhost:3012/queries/cypher?q='MATCH%20(people:Person)%20RETURN%20people.name%20LIMIT%2010;'

  But don't, really. Since we're going to delete this :-)"
  [conn]
  (fn [request]
    (->> [:params :q]
         (get-in request)
         (codec/percent-decode)
         (cypher/tquery conn)
         (response/json request))))

(defn cypher-injection-post
  "Call with something like this:

  $ curl -XPOST -H 'Content-Type: text/plain' http://localhost:3012/queries/cypher -d 'MATCH (people:Person) RETURN people.name LIMIT 10;'

  But don't, really. Since we're going to delete this :-)"
  [conn]
  (fn [request]
    (->> request
         :body
         slurp
         (cypher/tquery conn)
         (response/json request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ok
  (fn [request]
    (response/ok request)))

(def fallback
  (fn [request]
    (response/not-found request)))
