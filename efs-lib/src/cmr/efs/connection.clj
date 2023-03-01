(ns cmr.efs.connection
  "Contains functions for interacting with the EFS storage instance."
  (:require
   [clojure.java.io :as io]
   [clj-time.coerce :as cr]
   [cmr.common.config :refer [defconfig]]
   [cmr.efs.config :as efs-config]
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
  [provider concept-type concept-id concept]
  (let [concept-path (format "%s/%s/%s/%s-%s.r%i.zip" efs-config/efs-directory provider concept-type concept-id provider (:revision-id concept))]
    (io/make-parents (io/file concept-path))
    (spit (io/file concept-path) (:metadata concept))))

(defn get-concept
  "Gets a concept from EFS"
  [provider concept-type concept-id])

(defn delete-concept
  "Deletes a concept from EFS"
  [provider concept-type concept-id revision-id])
