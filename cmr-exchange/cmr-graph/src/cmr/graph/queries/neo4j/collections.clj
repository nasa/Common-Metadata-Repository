(ns cmr.graph.queries.neo4j.collections
  (:require
   [clojure.string :as string]))

(def reset "MATCH (n) DETACH DELETE n;")

(def get-all "MATCH (collection:Collection) RETURN collection;")
(def delete-all "MATCH (collection:Collection) DELETE collection;")
(def delete-all-cascade "MATCH (collection:Collection) DETACH DELETE collection;")

(defn get-urls-by-concept-id
  [concept-id]
  (format "match (c:Collection)-[:LINKS_TO]->(u:Url) where c.conceptId='%s' return u.name;"
          concept-id))

(defn get-concept-ids-by-urls
  [urls]
  (format "match (c:Collection)-[:LINKS_TO]->(u:Url) where u.name in [%s] return c.conceptId;"
          (string/join "," (map #(format "'%s'" %) urls))))

(defn get-urls-via-provider
  [provider]
  (format "match (p:Provider)<-[:OWNED_BY]-(c:Collection)-[:LINKS_TO]->(u:Url) where p.name='%s' return u.name;"
          provider))
