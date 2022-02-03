(ns cmr.ingest.services.granule-bulk-update.additional-file.umm-g
  "Contains functions to update UMM-G granule metadata for AdditionalFile granule bulk update."
  (:require
   [clojure.string :as string]
   [cmr.common-app.services.kms-fetcher :as kms-fetcher]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.core :as v-core]
   [cmr.ingest.validation.validation :as v-validation]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm-spec.validation.umm-spec-validation-core :as umm-spec-validation]))

(defn- get-new-sizes
  "Returns updated Size, SizeUnit, and SizeInBytes. This operation is unique from the others in that
   Size/SizeUnit or SizeInBytes can actually be removed by specifying a value of 0.
   File is the existing file, while input-file is the supplied file with the same name in the patch file."
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
  "Returns updated format, format type, and mime type..
   File is the existing file, while input-file is the supplied file with the same name in the patch file."
  [file input-file]
  {:Format (or (:Format input-file) (:Format file))
   :FormatType (or (:FormatType input-file) (:FormatType file))
   :MimeType (or (:MimeType input-file) (:MimeType file))})

(defn- get-new-checksum
  "Returns updated checksum value and algorithm.
   File is the existing file, while input-file is the supplied file with the same name in the patch file."
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
  "Merges file fields and returns in a clean order"
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
  "Builds an updated version of each File and FilePackage using the existing file and its matching input-file,
   if one was provided. Also calls itself on and File children of a FilePackage."
  [file input-files-map]
  (if-let [input-file (get input-files-map (:Name file))]
    (let [sizes (get-new-sizes file input-file)
          formats (get-new-formats file input-file)
          checksum (get-new-checksum file input-file)
          files (when (:Files file) ;ths function can call itself on any File children of a FilePackage
                  (map #(transform-file % input-files-map) (:Files file)))
          updated-file (merge-file-fields (:Name file) sizes formats checksum files)]
      (into {} (remove (comp nil? val) updated-file)))
    (if (:Files file) ;no matching input file, update any children and return
      (assoc file :Files (map #(transform-file % input-files-map) (:Files file)))
      file)))

(defn- update-additional-file-metadata
  "Prepares the metadata files for updating, checks for error cases. If the granule and input-Files
   are suitable for updates, maps the transform function to every File and FilePackage."
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

(defn- path-errors->scheme-error
  "Converts a validation error into a schema style error message for consistency
   with existing code. This translation is needed for enums which were
   previous checked against a Schema but are now checked against KMS"
  [validation-errors]
  (for [error validation-errors]
    (format "#/%s: %s" (string/replace
                        (string/join "/" (mapv str (:path error))) #":" "")
            (first (:errors error)))))

(defn update-additional-files
  "Updates the metadata Files and FilePackages contained under /DataGranule/ArchiveAndDistributionInformation.
   Optionally returns a list of unique validation errors which will be thrown if the resulting granule
   is saved."
  ([context umm-gran additional-files]
   (update-additional-files context umm-gran additional-files true))
  ([context umm-gran additional-files catch-errors]
   (let [updated-metadata (update-in umm-gran [:DataGranule :ArchiveAndDistributionInformation]
                                 #(update-additional-file-metadata % additional-files))
         kms-index (kms-fetcher/get-kms-index context)
         kms-errors (path-errors->scheme-error
                     (umm-spec-validation/validate-granule-without-collection
                      updated-metadata
                      (v-validation/bulk-granule-keyword-validations context)))
         schema-errors (umm-spec/validate-metadata :granule
                                                   :umm-json
                                                   (umm-json/umm->json updated-metadata))
         validation-errors (concat kms-errors schema-errors)]
     (when (and catch-errors (seq validation-errors))
       ;;normal validation only throws the first error in the seq, but we want to throw them all,
       ;;with the exception of "extraneous key" errors, which are compound errors resulting from
       ;;invalid keys (which is the only type of error we are expecting).
       (errors/throw-service-errors :invalid-data
        [(string/join "; " (set (remove #(re-seq #"extraneous key" %) validation-errors)))]))

     updated-metadata)))
