(ns cmr.graph.queries.neo4j.collections)

(def get-all "MATCH (c:Collection) RETURN c;")
(def delete-all "MATCH (c:Collection) DELETE c;")
