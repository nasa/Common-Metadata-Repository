(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.archive-and-dist-info
  "Functions for parsing UMM collection citation records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as dist-rel-url]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value gmx-anchor-value]]))

(defn parse-formats
  "Parses Format and FormatType values from ISO XML.
   The block-id is used to associated the correct values for each FileDistributionInformation."
  [formats]
  (for [format formats
        :let [format-name (char-string-value format "gmd:MD_Format/gmd:name")
              format-type (char-string-value format "gmd:MD_Format/gmd:specification")
              [href href-type block-id] (re-matches #"(.*)_(\d+)$" (or (get-in format [:attrs :xlink/href]) ""))]
        :when block-id]
    {:block-id (read-string block-id)
     :Format format-name
     :FormatType format-type}))

(defn parse-transfer-options
  "Parses Media, AverageFileSize, AverageFileSizeUnit, TotalCollectionFileSize and
   TotalCollectionFileSizeUnit values from ISO XML.
   The block-id is used to associated the correct values for each FileDistributionInformation."
  [transfer-options]
  (for [transfer-option transfer-options
        :let [[href href-type block-id] (re-matches #"(.*)_(\d+)$" (or (get-in transfer-option [:attrs :xlink/href] "")))]
        :when block-id
        :let [AverageFileSize (when (= href-type "FileDistributionInformation_AverageFileSize")
                                (value-of transfer-option "gmd:MD_DigitalTransferOptions/gmd:transferSize/gco:Real"))
              TotalCollectionFileSize (when (= href-type "FileDistributionInformation_TotalCollectionFileSize")
                                        (value-of transfer-option "gmd:MD_DigitalTransferOptions/gmd:transferSize/gco:Real"))]]
    (util/remove-nil-keys
     {:block-id (read-string block-id)
      :Media (when (= href-type "FileDistributionInformation_Media")
               [(value-of transfer-option "gmd:MD_DigitalTransferOptions/gmd:offLine/gmd:MD_Medium/gmd:name/gmd:MD_MediumNameCode/@codeListValue")])
      :AverageFileSize (when AverageFileSize
                         (read-string AverageFileSize))
      :AverageFileSizeUnit (when (= href-type "FileDistributionInformation_AverageFileSize")
                             (char-string-value transfer-option "gmd:MD_DigitalTransferOptions/gmd:unitsOfDistribution"))
      :TotalCollectionFileSize (when TotalCollectionFileSize
                                 (read-string TotalCollectionFileSize))
      :TotalCollectionFileSizeUnit (when (= href-type "FileDistributionInformation_TotalCollectionFileSize")
                                     (char-string-value transfer-option "gmd:MD_DigitalTransferOptions/gmd:unitsOfDistribution"))})))

(defn- parse-distributors
  "Parses Fees and Description values from ISO XML.
   The block-id is used to associated the correct values for each FileDistributionInformation."
  [distributors]
  (for [distributor distributors
        :let [[href href-type block-id] (re-matches #"(.*)_(\d+)$" (or (get-in distributor [:attrs :xlink/href]) ""))]
        :when block-id]
    {:block-id (read-string block-id)
     :Fees (char-string-value distributor "gmd:MD_Distributor/gmd:distributionOrderProcess/gmd:MD_StandardOrderProcess/gmd:fees")
     :Description (char-string-value distributor "gmd:MD_Distributor/gmd:distributionOrderProcess/gmd:MD_StandardOrderProcess/gmd:orderingInstructions")}))

(defn xml-dists->blocks
  "Parses ISO XML distributionFormat, transferOptions, and distributors then groups them by block-id."
  [doc dist-info-xpath]
  (let [formats (parse-formats
                 (select doc (str dist-info-xpath "/gmd:distributionFormat")))
        transfer-options (parse-transfer-options
                          (select doc (str dist-info-xpath "/gmd:transferOptions")))
        distributors (parse-distributors
                      (select doc (str dist-info-xpath "/gmd:distributor")))]
    (group-by :block-id (concat formats transfer-options distributors))))

(defn merge-block
  "Take each block-id block and merge the distributionFormat, transferOptions, and distributors
   associated with that block-id into a single map."
  [block]
  (dissoc
   (reduce merge {} block)
   :block-id))

(defn blocks->maps
  "Gathers up all the UMM FileDistributionInformation values by block-id.
   The subsequent calls to group-by THEN reduce maintains the order of the block-ids."
  [blocks]
  (when-let [blocks (vals blocks)]
    (map merge-block blocks)))

(defn parse-dist-info
  "Parses FileDistributionInformation from ISO MENDS and SMAP XML.
   dist-info-xpath is what differentiates between the two, the calling function will pass
   the relevant path."
  [doc dist-info-xpath]
  (let [blocks (xml-dists->blocks doc dist-info-xpath)]
    (blocks->maps blocks)))

(def specification-pattern
  (re-pattern "FormatType:|AverageFileSize:|AverageFileSizeUnit:|TotalCollectionFileSize:|TotalCollectionFileSizeUnit:|Description:"))

(defn parse-archive-info-specification
  "Parse all the FormatType, AverageFileSize, AverageFileSizeUnit, TotalCollectionFileSize,
   TotalCollectionFileSizeUnit and Description for FileArchiveInformation out of then specification.
   string."
  [archive]
  (when-let [spec-string (char-string-value archive "gmd:MD_Format/gmd:specification")]
   (let [format-type-index (util/get-index-or-nil spec-string "FormatType:")
         average-file-size-index (util/get-index-or-nil spec-string "AverageFileSize:")
         average-file-size-unit-index (util/get-index-or-nil spec-string "AverageFileSizeUnit:")
         total-collection-file-size-index (util/get-index-or-nil spec-string "TotalCollectionFileSize:")
         total-collection-file-size-unit-index (util/get-index-or-nil spec-string "TotalCollectionFileSizeUnit:")
         description-index (util/get-index-or-nil spec-string "Description:")]
    (when (or format-type-index
              average-file-size-index
              average-file-size-unit-index
              total-collection-file-size-index
              total-collection-file-size-unit-index
              description-index)
     (dist-rel-url/convert-key-strings-to-keywords
      (dist-rel-url/convert-iso-description-string-to-map spec-string specification-pattern))))))

(defn parse-archive-info
  "Parses FileArchiveInformation from ISO MENDS and SMAP XML.
   archive-info-xpath is what differentiates between the two, the calling function will pass
   the relevant path."
  [doc archive-info-xpath]
  (for [archive (select doc archive-info-xpath)
        :let [{:keys [FormatType AverageFileSize
                      AverageFileSizeUnit TotalCollectionFileSize
                      TotalCollectionFileSizeUnit Description]} (parse-archive-info-specification archive)]]
    {:Format (char-string-value archive "gmd:MD_Format/gmd:name")
     :FormatType FormatType
     :AverageFileSize (when AverageFileSize
                        (read-string AverageFileSize))
     :AverageFileSizeUnit AverageFileSizeUnit
     :TotalCollectionFileSize (when TotalCollectionFileSize
                                (read-string TotalCollectionFileSize))
     :TotalCollectionFileSizeUnit TotalCollectionFileSizeUnit
     :Description Description}))

(defn parse-archive-dist-info
  "Parses ArchiveAndDistributionInformation from ISO MENDS and SMAP XML."
  [doc archive-info-xpath dist-info-xpath]
  (let [file-dist-info (parse-dist-info doc dist-info-xpath)
        file-archive-info (parse-archive-info doc archive-info-xpath)]
    {:FileArchiveInformation (when (seq file-archive-info)
                               file-archive-info)
     :FileDistributionInformation (when (seq file-dist-info)
                                    file-dist-info)}))
