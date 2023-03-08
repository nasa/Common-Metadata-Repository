(ns cmr.efs.connection
  "Contains functions for interacting with the EFS storage instance."
  (:require
   [clojure.java.io :as io]
   [clj-time.coerce :as cr]
   [cmr.common.log :refer [debug error info trace warn]]
   [cmr.efs.config :as efs-config]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.health-helper :as hh])
  (:import
   [java.nio.file Files]))

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
  [provider concept-type concept]
  (let [concept-path (format "%s/%s/%s/%s.r%d.zip" (efs-config/efs-directory) (:provider-id provider) (name concept-type) (:concept-id concept) (:revision-id concept))]
    (info "Saving concept to EFS at path " concept-path)
    (io/make-parents (io/file concept-path))
    (.write Files concept-path (:metadata concept))))

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
