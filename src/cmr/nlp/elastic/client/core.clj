(ns cmr.nlp.elastic.client.core
  (:require
   [cheshire.core :as json]
   [cmr.nlp.elastic.client.index :as index]
   [taoensso.timbre :as log])
  (:import
   (org.apache.http HttpHost)
   (org.elasticsearch.action.admin.cluster.health ClusterHealthRequest)
   (org.elasticsearch.client RequestOptions
                             RestClient
                             RestHighLevelClient)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ElasticsearchAPI
  ;; Base API
  (close [this])
  ;; Cluster API
  (cluster [this])
  (health [this])
  ;; Index API
  (indices [this])
  (create-index [this index-name] [this index-name source])
  (delete-index [this index-name]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def -indices #(.indices %))

(defn -create-index*
  [this & args]
  (let [req (apply index/create args)
        resp (.create (-indices this) req RequestOptions/DEFAULT)]
    (.index resp)))

(defn -create-index
  ([this index-name]
    (-create-index* this index-name))
  ([this index-name source]
    (-create-index* this index-name source)))

(def -cluster #(.cluster %))

(defn -delete-index
  [this index-name]
  (let [req (index/delete index-name)
        resp (.delete (-indices this) req RequestOptions/DEFAULT)]
    (.isAcknowledged resp)))

(defn -health
  [this]
  (let [req (new ClusterHealthRequest)
        resp (.health (-cluster this) req RequestOptions/DEFAULT)]
    (-> resp
        str
        (json/parse-string true))))

(def behaviour
  {:create-index -create-index
   :close #(.close %)
   :cluster -cluster
   :delete-index -delete-index
   :health -health
   :indices -indices})

(extend RestHighLevelClient
        ElasticsearchAPI
        behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-host
  [cfg]
  (new HttpHost (:host cfg) (:port cfg) (:scheme cfg)))

(defn create
  [cfgs]
  (->> cfgs
       (map create-host)
       (into-array)
       (RestClient/builder)
       (new RestHighLevelClient)))
