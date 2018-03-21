(ns cmr.graph.queries.neo4j.collections
  (:require
   [clojure.string :as string]))

(def get-all "MATCH (collection:Collection) RETURN collection;")
(def delete-all "MATCH (collection:Collection) DELETE collection;")
(defn get-urls-by-concept-id
  [concept-id]
  (format "match (c:Collection)-[:HAS]->(u:URL) where c.ConceptId='%s' return u.Href;"
          concept-id))

(defn get-concept-ids-by-urls
  [urls]
  (format "match (c:Collection)-[:HAS]->(u:URL) where u.Href in [%s] return c.ConceptId;"
          (string/join "," (map #(format "'%s'" %) urls))))
