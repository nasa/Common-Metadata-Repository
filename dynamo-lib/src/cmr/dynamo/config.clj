(ns cmr.dynamo.config
  "Contains functions for retrieving important DynamoDB details from environment variables"
  (:require [cmr.common.config :as cfg :refer [defconfig]]))

(defconfig dynamo-table
   "DynamoDB table for meta-metadata items"
   {:default "meta-metadata"})

(defconfig dynamo-url
  "Endpoint to connect to DynamoDB"
  {:default "http://dynamodb.us-east-1.amazonaws.com"})

(defconfig dynamo-toggle
  "Three-way toggle for DynamoDB functionality. 'dynamo-off' uses only Oracle with EFS, 'dynamo-on' uses both Oracle and DynamoDB with EFS, and 'dynamo-only' uses only DynamoDB with EFS"
  {:default "dynamo-off"})
