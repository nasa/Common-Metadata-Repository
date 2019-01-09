(ns cmr.graph.data.statement
  "Functions for converting a collection into neo4j create statements."
  (:require
   [clojure.string :as string]
   [digest :as digest]))

(defn- coll-concept-id-key
  [coll]
  (string/replace (first (:concept-id coll)) "-" ""))

(defn- coll->provider-node
  [coll]
  (let [provider-id (first (:provider-id coll))]
    [(format "CREATE (%s:Provider {ShortName:'%s'})"
             provider-id provider-id)]))

(defn- coll->coll-stmts
  [coll]
  (let [{:keys [provider-id concept-id entry-id version-id]} coll
        provider-id (first provider-id)
        concept-id (first concept-id)
        entry-id (first entry-id)
        version-id (first version-id)]
    [(format "CREATE (%s:Collection {conceptId: '%s', entryId:'%s', version:'%s'})"
             (coll-concept-id-key coll) concept-id entry-id version-id)
     (format "CREATE (%s)-[:PROVIDES {type:['collection']}]->(%s)"
             provider-id (coll-concept-id-key coll))]))

(defn- url-type->stmt
  [url-type coll-key]
  (let [url-type-key (str "A" (digest/md5 url-type))]
  [(format "CREATE (%s:UrlType {Value:'%s'})"
           url-type-key url-type)
   (format "CREATE (%s)-[:LINKS {type:['collection']}]->(%s)"
           url-type-key coll-key)]))

(defn- url->stmt
  [url coll-key]
  (let [url-key (str "A" (digest/md5 url))]
    [(format "CREATE (%s:Url {name:'%s'})"
             url-key url)
     (format "CREATE (%s)-[:LINKS_TO]->(%s)"
             coll-key url-key)
     (format "CREATE (%s)-[:IS_IN]->(%s)"
             url-key coll-key)]))

(defn- coll->related-urls-stmts
  [coll]
  (let [{:keys [related-urls]} coll
        url-types (distinct (mapv :type related-urls))
        urls (distinct (mapv :url related-urls))
        coll-key (coll-concept-id-key coll)]
    (concat (mapcat #(url-type->stmt % coll-key) url-types)
            (mapcat #(url->stmt % coll-key) urls))))

(defn coll->create-stmts
  "Returns the neo4j create statements for the given collection"
  [coll]
  (concat
   (coll->provider-node coll)
   (coll->coll-stmts coll)
   (coll->related-urls-stmts coll)))

(defn neo4j-statements
  "Returns the neo4j statements for building the graph of the given collections."
  [colls]
  (string/join "\n" (distinct (mapcat coll->create-stmts colls))))
