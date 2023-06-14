(ns cmr.dynamo.connection
  "Contains functions for interacting with the DynamoDB storage instance"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.common.log :refer [debug error info trace warn]]
            [taoensso.faraday :as far]
            [cmr.dynamo.config :as dynamo-config]))

(defn concept-revision-tuple->key
  [tuple]
  {:concept-id (first tuple) :revision-id (second tuple)})

(defn provider-concept-revision-tuple->key
  [tuple]
  {:concept-id (nth tuple 1) :revision-id (nth tuple 2)})

(def connection-options 
  {:endpoint (dynamo-config/dynamo-url)})

(defn save-concept
  [concept]
  (far/put-item connection-options (dynamo-config/dynamo-table) concept))

(defn get-concept
  "Gets a concept from DynamoDB"
  ([concept-id]
   (far/query connection-options (dynamo-config/dynamo-table) {:concept-id [:eq concept-id]} {:order :desc :limit 1}))
  ([concept-id revision-id]
   (far/get-item connection-options (dynamo-config/dynamo-table) {:concept-id [:eq concept-id] :revision-id [:eq revision-id]})))

(defn get-concepts-provided
  "Gets a group of concepts from DynamoDB"
  [concept-id-revision-id-tuples]
  (map (fn [batch] (far/batch-get-item connection-options {(dynamo-config/dynamo-table) {:prim-kvs (vec batch)}})) (partition-all 100 (map concept-revision-tuple->key concept-id-revision-id-tuples))))

(defn get-concepts
  "Gets a group of concepts from DynamoDB based on search parameters"
  [params]
  (info "Params for searching DynamoDB: " params))

(defn get-concepts-small-table
  "Gets a group of concepts from DynamoDB using provider-id, concept-id, revision-id tuples"
  [params]
  (info "Params for searching DynamoDB small-table: " params))

(defn delete-concept
  "Deletes a concept from DynamoDB"
  [concept-id revision-id]
  (far/delete-item connection-options (dynamo-config/dynamo-table) {:concept-id concept-id :revision-id revision-id}))

(defn delete-concepts-provided
  "Deletes multiple concepts from DynamoDB"
  [concept-id-revision-id-tuples]
  (map (fn [batch] (far/batch-write-item connection-options {(dynamo-config/dynamo-table) {:delete (vec batch)}})) (partition-all 25 (map concept-revision-tuple->key concept-id-revision-id-tuples))))

(defn delete-concepts
  "Deletes multiple concepts from DynamoDB by search parameters"
  [params]
  (info "Params for deleting from DynamoDB: " params))