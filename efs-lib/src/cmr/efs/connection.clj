(ns cmr.efs.connection
  "Contains functions for interacting with the EFS storage instance."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clj-time.coerce :as cr]
   [cmr.common.log :refer [debug error info trace warn]]
   [cmr.efs.config :as efs-config]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.health-helper :as hh])
  (:import
   (java.nio.file Files
                  Paths
                  OpenOption)
   (java.io File)))

;;--------------------- UTILITY FUNCTIOINS ---------------------
(defn get-revision-file-names
  "Returns a listing of revisions of the concept stored on EFS"
  [provider concept-type concept-id]
  (let [concept-dir-path (format "%s/%s/%s/%s" (efs-config/efs-directory) (:provider-id provider) (name concept-type) concept-id)]
    (map (fn [file]
           (.getName file)) (.listFiles (File. concept-dir-path)))))

(defn get-latest-revision
  [provider concept-type concept-id]
  (first (sort > (map (fn [file-name]
                        (Integer/parseInt (subs (second (str/split file-name #"\.")) 1))) (get-revision-file-names provider concept-type concept-id)))))

(defn concept-revision-exists
  [provider concept-type concept-id revision-id]
  (let [concept-path (format "%s/%s/%s/%s/%s.r%d.zip" (efs-config/efs-directory) provider concept-type concept-id concept-id revision-id)]
    (.exists (File. concept-path))))

;;--------------------- CORE FUNCTIONS ---------------------

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
  (let [concept-path (format "%s/%s/%s/%s/%s.r%d.zip" (efs-config/efs-directory) (:provider-id provider) (name concept-type) (:concept_id concept) (:concept_id concept) (:revision_id concept))]
    (info "Saving concept to EFS at path " concept-path)
    (io/make-parents (io/file concept-path))
    (Files/write (Paths/get concept-path (into-array String [])) (:metadata concept) (into-array OpenOption []))))

(defn get-concept
  "Gets a concept from EFS"
  ([provider concept-type concept-id]
   (get-concept provider concept-type concept-id (get-latest-revision provider concept-type concept-id)))
  ([provider concept-type concept-id revision-id]
   (if (and revision-id
            (concept-revision-exists provider concept-type concept-id revision-id))
     (let [concept-path (format "%s/%s/%s/%s/%s.r%d.zip" (efs-config/efs-directory) (:provider-id provider) (name concept-type) concept-id concept-id revision-id)]
       (info "Getting concept from EFS at path " concept-path)
       {:revision-id revision-id :metadata (Files/readAllBytes (Paths/get concept-path (into-array String [])))})
     nil)))

(defn get-concepts
  "Gets a group of concepts from EFS"
  [provider concept-type concept-id-revision-id-tuples]
  (info "Keys in revision tuple: " (first concept-id-revision-id-tuples))
  (remove nil? (doall (map (fn [tuple] (get-concept provider concept-type (first tuple) (second tuple))) concept-id-revision-id-tuples))))

(defn delete-concept
  "Deletes a concept from EFS"
  [provider concept-type concept-id revision-id])

(defn delete-concepts
  "Deletes multiple concepts from EFS"
  [provider concept-type concept-id-revision-id-tuples])
