(ns cmr.dynamo.connection
  "Contains functions for interacting with the DynamoDB storage instance"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [taoensso.faraday :as far]))

(def connection-options 
  {:endpoint "http://dynamodb.us-east-1.amazonaws.com"})

(defn save-concept
  [concept])

(defn get-concept
  "Gets a concept from DynamoDB"
  ([provider concept-type concept-id]))

(defn get-concepts
  "Gets a group of concepts from DynamoDB"
  [provider concept-type concept-id-revision-id-tuples]
  ())

(defn get-concepts-small-table
  "Gets a group of concepts from DynamoDB using provider-id, concept-id, revision-id tuples"
  [concept-type provider-concept-revision-tuples]
  ())

(defn delete-concept
  "Deletes a concept from DynamoDB"
  [provider concept-type concept-id revision-id]
  ())

(defn delete-concepts
  "Deletes multiple concepts from DynamoDB"
  [provider concept-type concept-id-revision-id-tuples]
  ())
