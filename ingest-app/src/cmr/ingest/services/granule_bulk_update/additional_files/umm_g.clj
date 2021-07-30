(ns cmr.ingest.services.granule-bulk-update.additional-files.umm-g
  "Contains functions to update UMM-G granule metadata for OPeNDAP url bulk update."
  (:require
   [cmr.common.services.errors :as errors]))

(defn- appended-related-urls
  "Does another thing"
  [files input-files])


(defn update-additional-files
  "Does a thing"
  [umm-gran additional-files]
  (println umm-gran)
  (println additional-files)
  umm-gran)
  ;(update umm-gran [:DataGranule :ArchiveAndDistributionInformation]
          ;#(update-additional-files % additionalfiles))
