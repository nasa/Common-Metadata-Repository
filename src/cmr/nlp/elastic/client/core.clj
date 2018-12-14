(ns cmr.nlp.elastic.client.core
  (:require
   [cheshire.core :as json]
   [cmr.nlp.elastic.client.document :as document]
   [cmr.nlp.elastic.client.index :as index]
   [cmr.nlp.elastic.client.pipeline :as pipeline]
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
  ;; Document API
  (bulk [this requests-data])
  (index [this index-name doc-type doc-id source])
  (delete-document [this index-name doc-type doc-id])
  (update-document [this index-name doc-type doc-id source])
  (upsert-document [this index-name doc-type doc-id source])
  ;; Cluster API
  (cluster [this])
  (health [this])
  ;; Indices API
  (indices [this])
  (create-index [this index-name] [this index-name source])
  (delete-index [this index-name])
  ;; Pipeline API
  (delete-pipeline [this id])
  (get-pipeline [this id])
  (put-pipeline [this id source] [this id source content-type]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Bulk API

;; Cluster API

(def -cluster #(.cluster %))

(defn -health
  [this]
  (let [req (new ClusterHealthRequest)
        resp (.health (-cluster this) req RequestOptions/DEFAULT)]
    (-> resp
        str
        (json/parse-string true))))

;; Document API

(defn -index
  [this index-name doc-type doc-id source]
  (let [req (document/index index-name doc-type doc-id source)
        resp (.index this req RequestOptions/DEFAULT)]
    (.getIndex resp)))

(defn -bulk
  [this reqs-data]
  (let [req (document/bulk reqs-data)
        resp (.bulk this req RequestOptions/DEFAULT)]
    (log/info "Bulk document operation took:" (str (.getTook resp)))
    (.getItems resp)))

(defn -delete-document
  [this index-name doc-type doc-id]
  (let [req (document/delete index-name doc-type doc-id)
        resp (.delete this req RequestOptions/DEFAULT)]
    (.getIndex resp)))

(defn -update-document
  [this index-name doc-type doc-id source]
  (let [req (document/update index-name doc-type doc-id source)
        resp (.update this req RequestOptions/DEFAULT)]
    (.getIndex resp)))

(defn -upsert-document
  [this index-name doc-type doc-id source]
  (let [req (document/upsert index-name doc-type doc-id source)
        resp (.update this req RequestOptions/DEFAULT)]
    (.getIndex resp)))

;; Indices API

(def -indices #(.indices %))

(defn- create-index*
  [this & args]
  (let [req (apply index/create args)
        resp (.create (-indices this) req RequestOptions/DEFAULT)]
    (.index resp)))

(defn -create-index
  ([this index-name]
    (create-index* this index-name))
  ([this index-name source]
    (create-index* this index-name source)))

(defn -delete-index
  [this index-name]
  (let [req (index/delete index-name)
        resp (.delete (-indices this) req RequestOptions/DEFAULT)]
    (.isAcknowledged resp)))

;; Ingest API

(def -ingest #(.ingest %))

(defn -delete-pipeline
  [this id]
  (let [req (pipeline/get this id)
        resp (.deletePipeline (-ingest this) req RequestOptions/DEFAULT)]
    (.isAcknowledged resp)))

(defn -get-pipeline
  [this id]
  (let [req (pipeline/get this id)
        resp (.getPipeline (-ingest this) req RequestOptions/DEFAULT)]
    (.pipelines resp)))

(defn -put-pipeline
  ([this id source]
    (-put-pipeline this id source :json))
  ([this id source content-type]
    (let [req (pipeline/put this id source content-type)
          resp (.putPipeline (-ingest this) req RequestOptions/DEFAULT)]
      (.isAcknowledged resp))))

(def behaviour
  {:bulk -bulk
   :create-index -create-index
   :close #(.close %)
   :cluster -cluster
   :delete-document -delete-document
   :delete-index -delete-index
   :delete-pipeline -delete-pipeline
   :get-pipeline -get-pipeline
   :health -health
   :indices -indices
   :index -index
   :ingest -ingest
   :put-pipeline -put-pipeline
   :update-document -update-document
   :upsert-document -upsert-document})

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
