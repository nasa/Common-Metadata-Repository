(ns cmr.graph.collections.core
  (:require
   [cheshire.core :as json]
   [clojurewerkz.neocons.rest.cypher :as cypher]
   [clojurewerkz.neocons.rest.nodes :as nodes]
   [clojurewerkz.neocons.rest.relationships :as relations]
   [cmr.graph.queries.neo4j.collections :as query]))

(defn reset
  [conn]
  (cypher/tquery conn query/reset))

(defn get-all
  [conn]
  (cypher/tquery conn query/get-all))

(defn delete-all
  [conn]
  (cypher/tquery conn query/delete-all))

(defn delete-all-cascade
  [conn]
  (cypher/tquery conn query/delete-all-cascade))

(defn batch-add
  [conn ^String json]
  )

(defn add-collection
  [conn ^String json]
  )

(defn get-collection
  [conn ^String concept-id]
  )

(defn delete-collection
  [conn ^String concept-id]
  )

(defn update-collection
  [conn ^String concept-id]
  )

(defn get-collections-via-related-urls
  [conn ^String concept-id]
  (cypher/tquery conn (query/get-urls-by-concept-id concept-id)))

(defn get-concept-ids-by-urls
  [conn urls]
  (cypher/tquery conn (query/get-concept-ids-by-urls urls)))

(defn get-urls-via-provider
  [conn provider]
  (cypher/tquery conn (query/get-urls-via-provider provider)))
