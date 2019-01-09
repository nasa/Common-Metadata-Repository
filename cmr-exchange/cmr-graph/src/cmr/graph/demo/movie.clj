(ns cmr.graph.demo.movie
  (:require
   [clojure.string :as string]
   [clojurewerkz.neocons.rest :as nr]
   [clojurewerkz.neocons.rest.cypher :as cy]
   [cmr.graph.queries.neo4j.demo.movie :as query]
   [taoensso.timbre :as log]))

(defn get-graph
  ([conn]
    (get-graph conn 100))
  ([conn limit]
    (when (string? limit)
      (get-graph conn (Integer/parseInt limit)))
    (let   [result (cy/tquery conn query/graph {:limit limit})
            nodes (map (fn [{:strs [cast movie]}]
                         (concat [{:title movie
                                   :label :movie}]
                                 (map (fn [x] {:title x
                                               :label :actor})
                                      cast)))
                       result)
            nodes (distinct (apply concat nodes))
            nodes-index (into {} (map-indexed #(vector %2 %1) nodes))
            links (map (fn [{:strs [cast movie]}]
                         (let [target   (nodes-index {:title movie :label :movie})]
                           (map (fn [x]
                                  {:target target
                                   :source (nodes-index {:title x :label :actor})})
                                       cast)))
                       result)]
    {:nodes nodes :links (flatten links)})))

(defn search
  [conn q]
  (if (string/blank? q)
    []
    (let  [result (cy/tquery conn query/search {:title (str "(?i).*" q ".*")})]
      (map (fn [x] {:movie (:data (x "movie"))}) result))))

(defn get-movie
  [conn title]
  (log/trace "Got connection:" conn)
  (log/trace "Got title:" title)
  (let [[result] (cy/tquery conn query/title {:title title})]
    result))
