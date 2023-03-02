(ns cmr.efs.connection
  "Contains functions for interacting with the EFS storage instance."
  (:require
   [clj-time.coerce :as cr]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.health-helper :as hh]))

(defn health-fn
  "Returns the health status of the EFS instance."
  []
  )

(defn health
  "Returns the efs health with timeout handling."
  []
  ())

(defn save-concept
  "Saves a concept to EFS"
  [provider concept-type concept-id])

(defn get-concept
  "Gets a concept from EFS"
  ([provider concept-type concept-id])
  ([provider concept-type concept-id revision-id]))

(defn get-concepts
  "Gets a concept from EFS"
  [provider concept-type concept-id-revision-id-tuples])

(defn delete-concept
  "Deletes a concept from EFS"
  [provider concept-type concept-id revision-id])

(defn delete-concepts
  "Deletes multiple concepts from EFS"
  [provider concept-type concept-id-revision-id-tuples])
