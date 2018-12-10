(ns cmr.nlp.elastic.client
  (:require
   [cheshire.core :as json]
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
  (close [this])
  (cluster [this])
  (health [this]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def -cluster #(.cluster %))

(defn -health
  [this]
  (let [req (new ClusterHealthRequest)
        resp (.health (-cluster this) req RequestOptions/DEFAULT)]
    (-> resp
        str
        (json/parse-string true))))

(def behaviour
  {:close #(.close %)
   :cluster -cluster
   :health -health})

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
