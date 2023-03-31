(ns cmr.efs.connection
  "Contains functions for interacting with the EFS storage instance."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clj-time.coerce :as cr]
   [cmr.common.log :refer [debug error info trace warn]]
   [cmr.common.util :as util]
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
(defn make-concept-dir-path
  [provider concept-type concept-id]
  (format "%s/%s/%s/%s" (efs-config/efs-directory) provider concept-type concept-id))

(defn get-revision-file-names
  "Returns a listing of revisions of the concept stored on EFS"
  [provider concept-type concept-id]
  (let [concept-dir-path (make-concept-dir-path provider concept-type concept-id)]
    (map (fn [file]
           (.getName file))
         (.listFiles (File. concept-dir-path)))))

(defn get-revision-from-filename
  "Gets the revision number from the filename"
  [filename]
  (Integer/parseInt (subs (second (str/split filename #"\.")) 1)))

(defn get-list-of-revisions
  "Gets simply the revision numbers of the files in sorted order smallest to largest"
  [provider concept-type concept-id]
  (sort-by get-revision-from-filename (get-revision-file-names provider concept-type concept-id)))

(defn get-latest-revision
  [provider concept-type concept-id]
  (first (sort > (map get-revision-from-filename
                      (get-revision-file-names provider concept-type concept-id)))))

(defn make-concept-path
  ([provider concept-type concept]
   (format "%s/%s/%s/%s/%s.r%s.zip" (efs-config/efs-directory) (:provider-id provider) (name concept-type) (:concept_id concept) (:concept_id concept) (:revision_id concept)))
  ([provider concept-type concept-id revision-id]
   (format "%s/%s/%s/%s/%s.r%s.zip" (efs-config/efs-directory) (:provider-id provider) (name concept-type) concept-id concept-id revision-id)))

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
  (let [concept-path (make-concept-path provider concept-type concept)]
    (info "Saving concept to EFS at path " concept-path)
    (io/make-parents (io/file concept-path))
    (Files/write (Paths/get concept-path (into-array String [])) (:metadata concept) (into-array OpenOption []))))

(defn get-concept
  "Gets a concept from EFS"
  ([provider concept-type concept-id]
   (get-concept provider concept-type concept-id (get-latest-revision (:provider-id provider) (name concept-type) concept-id)))
  ([provider concept-type concept-id revision-id]
   (when revision-id
     (let [concept-path (make-concept-path provider concept-type concept-id revision-id)]
       (try
         (info "Getting concept from EFS at path " concept-path)
         {:revision-id revision-id :metadata (util/gzip-bytes->string (Files/readAllBytes (Paths/get concept-path (into-array String [])))) :deleted false}
         (catch Exception e
           (info "Exception returned from EFS get concept at path: " concept-path " Exception value: " e)))))))

(defn get-concepts
  "Gets a group of concepts from EFS"
  [provider concept-type concept-id-revision-id-tuples]
  (doall (remove nil? (map (fn [tuple] (get-concept provider concept-type (first tuple) (second tuple))) concept-id-revision-id-tuples))))

(defn delete-concept
  "Deletes a concept from EFS"
  [provider concept-type concept-id revision-id]
  (let [concept-path (make-concept-path provider concept-type concept-id revision-id)]
    (info "Removing concept from EFS: " concept-path)
    (Files/deleteIfExists (Paths/get concept-path (into-array String [])))))

(defn delete-concepts
  "Deletes multiple concepts from EFS"
  [provider concept-type concept-id-revision-id-tuples]
  (info "EFS delete concepts: " concept-id-revision-id-tuples)
  (map (fn [tuple] (delete-concept provider concept-type (first tuple) (second tuple))) concept-id-revision-id-tuples))
