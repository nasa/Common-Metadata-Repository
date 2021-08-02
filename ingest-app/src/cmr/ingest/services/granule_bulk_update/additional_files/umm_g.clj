(ns cmr.ingest.services.granule-bulk-update.additional-files.umm-g
  "Contains functions to update UMM-G granule metadata for OPeNDAP url bulk update."
  (:require
   [cmr.common.services.errors :as errors]
   [clojure.pprint :as pprint]))

(defn- get-new-sizes
  "Returns updated size values.
   File is the old file, while input-file is the supplied file with the same name in the patch file."
  [file input-file]
  (when (and (:Size file)
             (not (or (:SizeUnit file) (:SizeUnit input-file)))))
  {(or (:SizeInBytes input-file) (:SizeInBytes file))
   (or (:Size input-file) (:Size file))
   (or (:SizeUnit input-file) (:SizeUnit file))})


(defn- transform-file
  "Does third thing"
  [file input-files-map input-filenames]
  (let [input-file (get input-files-map (:Name file))
        sizes (get-new-sizes file input-file)
        files (if (:Files file)
                {:Files (map #(transform-file % input-files-map input-filenames) (:Files file))}
                {})]
    (merge file sizes files)))


(defn- update-additional-file-metadata
  "Does another thing"
  [files input-files]
  (let [input-filenames (map :Name input-files)
        input-files-2-vectors (map (juxt :Name identity) input-files)
        input-files-map (into (sorted-map) (vec input-files-2-vectors))
        updated-files (map #(transform-file % input-files-map input-filenames) files)]
    updated-files))


(defn update-additional-files
  "Does a thing"
  [umm-gran additional-files]
  (update-in umm-gran [:DataGranule :ArchiveAndDistributionInformation]
         #(update-additional-file-metadata % additional-files)))
