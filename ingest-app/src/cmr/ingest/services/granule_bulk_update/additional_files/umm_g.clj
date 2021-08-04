(ns cmr.ingest.services.granule-bulk-update.additional-files.umm-g
  "Contains functions to update UMM-G granule metadata for OPeNDAP url bulk update."
  (:require
   [cmr.common.services.errors :as errors]
   [clojure.pprint :as pprint]))

(defn- get-new-sizes
  "Returns updated size values.
   File is the old file, while input-file is the supplied file with the same name in the patch file."
  [file input-file]
  (let [new-size-and-unit (if (= 0 (:Size input-file))
                            {} ;if 0 is specified, do not include size and size-unit
                            {:Size (or (:Size input-file) (:Size file))
                             :SizeUnit (or (:SizeUnit input-file) (:SizeUnit file))})
        new-size-in-bytes (if (= 0 (:SizeInBytes input-file))
                             {} ; if 0 is specified, do not include size-in-bytes
                             {:SizeInBytes (or (:SizeInBytes input-file) (:SizeInBytes file))})]
    (when (and (:Size new-size-and-unit)
               (not (:SizeUnit new-size-and-unit)))
      (errors/throw-service-errors :invalid-data
        ["Can't update granule: size value supplied with no sizeunit present"]))
    (merge new-size-in-bytes new-size-and-unit)))

(defn- get-new-formats
  "gets new formats"
  [file input-file]
  {:Format (or (:Format input-file) (:Format file))
   :FormatType (or (:FormatType input-file) (:FormatType file))
   :MimeType (or (:MimeType input-file) (:MimeType file))})

(defn- get-new-checksum
  "Returns updated checksum value and algorithm.
   File is the old file, while input-file is the supplied file with the same name in the patch file."
  [file input-file]
  (let [old-checksum (get file :Checksum)
        input-checksum (get input-file :Checksum)
        value (or (:Value input-checksum) (:Value old-checksum))
        algorithm (or (:Algorithm input-checksum) (:Algorithm old-checksum))]
    (when (and value (not algorithm))
      (errors/throw-service-errors :invalid-data
       ["Can't update granule: checksum value supplied with no algorithm present"]))
    (when (and algorithm (not value))
      (errors/throw-service-errors :invalid-data
       ["Can't update granule: checksum algorithm supplied with no value present"]))
    (when (and value algorithm)
      {:Value value :Algorithm algorithm})))

(defn- merge-file-fields
  "Merges file fields"
  [filename sizes formats checksum files]
  ;array-map used to enforce a clean order for updated files
  (array-map :Name filename
             :SizeInBytes (:SizeInBytes sizes)
             :Size (:Size sizes)
             :SizeUnit (:SizeUnit sizes)
             :Format (:Format formats)
             :FormatType (:FormatType formats)
             :MimeType (:MimeType formats)
             :Checksum checksum
             :Files files))

(defn- transform-file
  "Does third thing"
  [file input-files-map]
  (if-let [input-file (get input-files-map (:Name file))]
    (let [sizes (get-new-sizes file input-file)
          formats (get-new-formats file input-file)
          checksum (get-new-checksum file input-file)
          files (when (:Files file)
                  (map #(transform-file % input-files-map) (:Files file)))
          updated-file (merge-file-fields (:Name file) sizes formats checksum files)]
      (into {} (remove (comp nil? val) updated-file)))
    (if (:Files file) ;no matching input file, update any children and return
      (assoc file :Files (map #(transform-file % input-files-map) (:Files file)))
      file)))

(defn- update-additional-file-metadata
  "Does another thing"
  [files input-files]
  (let [sub-file-names (->> files (map :Files) (flatten) (map :Name))
        file-names (vec (remove nil? (concat sub-file-names (map :Name files))))
        input-file-names (mapv :Name input-files)
        input-files-2-vectors (map (juxt :Name identity) input-files)
        input-files-map (into (sorted-map) (vec input-files-2-vectors))]
    (when-not (every? (set file-names) input-file-names)
      (errors/throw-service-errors :invalid-data
        ["Update failed - please only specify Files or FilePackages contained in the"
          "existing granule metadata"]))
    (when-not (= (count (set file-names)) (count file-names))
      (errors/throw-service-errors :invalid-data
        ["Update failed - this operation is not available for granules with duplicate"
          "FilePackage/File names in the granule metadata."]))
    (when-not (= (count (set input-file-names)) (count input-file-names))
      (errors/throw-service-errors :invalid-data
        ["Update failed - duplicate files provided for granule update"]))
    (map #(transform-file % input-files-map) files)))

(defn update-additional-files
  "Does a thing"
  [umm-gran additional-files]
  (update-in umm-gran [:DataGranule :ArchiveAndDistributionInformation]
         #(update-additional-file-metadata % additional-files)))
