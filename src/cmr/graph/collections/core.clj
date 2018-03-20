(ns cmr.graph.collections.core
  (:require
   [clojurewerkz.neocons.rest.cypher :as cy]
   [cmr.graph.queries.neo4j.collections :as query]))

(defn get-all
  [conn]
  (cy/tquery conn query/get-all))

(defn delete-all
  [conn]
  (cy/tquery conn query/delete-all))
