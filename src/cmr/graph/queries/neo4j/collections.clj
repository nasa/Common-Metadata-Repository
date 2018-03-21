(ns cmr.graph.queries.neo4j.collections)

(def get-all "MATCH (collection:Collection) RETURN collection;")
(def delete-all "MATCH (collection:Collection) DELETE collection;")
