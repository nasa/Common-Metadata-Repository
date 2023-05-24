(ns cmr.dynamo.config
  "Contains functions for retrieving important DynamoDB details from environment variables"
  (:require [cmr.common.config :as cfg :refer [defconfig]]))

(defconfig dynamo-table
   "DynamoDB table for meta-metadata items"
   {:default "meta-metadata"})

(defconfig dynamo-toggle
  "Three-way toggle for DynamoDB functionality. 'dynamo-off' uses only Oracle, 'dynamo-on' uses both Oracle and EFS, and 'dynamo-only' uses only EFS"
  {:default "dynamo-off"})