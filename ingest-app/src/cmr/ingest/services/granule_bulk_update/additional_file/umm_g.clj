(ns cmr.ingest.services.granule-bulk-update.additional-file.umm-g
  "Contains functions to update UMM-G granule metadata for OPeNDAP url bulk update."
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))


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
        [(format (str "Can't update granule: Size value supplied with no SizeUnit present"
                      " for File or FilePackage with name [%s]")
                 (:Name file))]))
    (when (and (:SizeUnit new-size-and-unit)
               (not (:Size new-size-and-unit)))
      (errors/throw-service-errors :invalid-data
        [(format (str "Can't update granule: SizeUnit value supplied with no Size present"
                      " for File or FilePackage with name [%s]")
                 (:Name file))]))
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
  (let [old-checksum (:Checksum file)
        input-checksum (:Checksum input-file)
        value (or (:Value input-checksum) (:Value old-checksum))
        algorithm (or (:Algorithm input-checksum) (:Algorithm old-checksum))]
    (when (and (:Algorithm input-checksum) (not (:Value input-checksum)))
      (errors/throw-service-errors :invalid-data
       [(format (str "Can't update granule: checksum algorithm update requested without new checksum value"
                     " for file with name [%s]")
                (:Name file))]))
    (when (and value (not algorithm))
      (errors/throw-service-errors :invalid-data
       [(format (str "Can't update granule: checksum value supplied with no algorithm present"
                     " for file with name [%s]")
                (:Name file))]))
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
        [(str "Update failed - please only specify Files or FilePackages contained in the"
              " existing granule metadata")]))
    (when-not (= (count (set file-names)) (count file-names))
      (errors/throw-service-errors :invalid-data
        [(str "Update failed - this operation is not available for granules with duplicate"
              " FilePackage/File names in the granule metadata.")]))
    (when-not (= (count (set input-file-names)) (count input-file-names))
      (errors/throw-service-errors :invalid-data
        ["Update failed - duplicate files provided for granule update"]))
    (map #(transform-file % input-files-map) files)))

(defn update-additional-files
  "Does a thing"
  ([umm-gran additional-files]
   (update-additional-files umm-gran additional-files true))
  ([umm-gran additional-files catch-errors]
   (let [updated-metadata (update-in umm-gran [:DataGranule :ArchiveAndDistributionInformation]
                                 #(update-additional-file-metadata % additional-files))
         validation-errors (umm-spec/validate-metadata :granule
                                     :umm-json
                                     (umm-json/umm->json updated-metadata))]

     (when (and catch-errors (seq validation-errors))
       ;;normal validation only throws the first error in the seq, but we want to throw them all,
       ;;with the exception of "extraneous key" errors, which are compound errors resulting from
       ;;invalid keys (which is the only type of error we are expecting).
       (errors/throw-service-errors :invalid-data
        [(string/join "; " (set (remove #(re-seq #"extraneous key" %) validation-errors)))]))

     updated-metadata)))
