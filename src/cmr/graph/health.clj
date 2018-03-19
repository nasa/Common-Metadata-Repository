(ns cmr.graph.health
  (:require
   [clj-http.client :as httpc]))

(defn http-ok?
  [url]
  (if (= 200 (:status (httpc/head url)))
    true
    false))

(defn has-data?
  [x]
  (if (nil? x)
    false
    true))

(defn config-ok?
  [component]
  (has-data? (:config component)))

(defn elastic-ok?
  [component]
  (http-ok? (get-in component [:elastic :conn :uri])))

(defn logging-ok?
  [component]
  (has-data? (:logging component)))

(defn neo4j-ok?
  [component]
  (http-ok? (get-in component [:neo4j :conn :endpoint :uri])))

(defn components-ok?
  [component]
  {:config {:ok? (config-ok? component)}
   :httpd {:ok? true}
   :elastic {:ok? (elastic-ok? component)}
   :logging {:ok? (logging-ok? component)}
   :neo4j {:ok? (neo4j-ok? component)}})
