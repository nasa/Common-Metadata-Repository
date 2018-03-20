(ns cmr.graph.collections.core
  (:require
   [cheshire.core :as json]
   [clojurewerkz.neocons.rest.cypher :as cy]
   [cmr.graph.queries.neo4j.collections :as query]))

(defn get-all
  [conn]
  (cy/tquery conn query/get-all))

(defn delete-all
  [conn]
  (cy/tquery conn query/delete-all))

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
