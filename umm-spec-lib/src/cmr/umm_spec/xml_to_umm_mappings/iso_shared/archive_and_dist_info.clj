(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.archive-and-dist-info
  "Functions for parsing UMM collection citation records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as parsing-util]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value gmx-anchor-value]]))

(defn parse-formats
  "Parses Format and FormatType values from ISO XML.
   The block-id is used to associated the correct values for each FileDistributionInformation."
  [formats]
  (for [format formats
        :let [format-name (char-string-value format "gmd:MD_Format/gmd:name")
              specification (char-string-value format "gmd:MD_Format/gmd:specification")
              specification-map (when specification
                                  (parsing-util/convert-iso-description-string-to-map
                                    specification
                                    (re-pattern "FormatType:|FormatDescription:")))
              [href href-type block-id] (re-matches #"(.*)_(\d+)$" (or (get-in format [:attrs :xlink/href]) ""))]
        :when block-id]
    {:block-id (read-string block-id)
     :Format format-name
     :FormatType (:FormatType specification-map)
     :FormatDescription (:FormatDescription specification-map)}))

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
  (re-pattern "FormatType:|FormatDescription:|AverageFileSize:|AverageFileSizeUnit:|TotalCollectionFileSize:|TotalCollectionFileSizeUnit:|Description:"))

(defn parse-archive-info-specification
  "Parse all the FormatType, AverageFileSize, AverageFileSizeUnit, TotalCollectionFileSize,
   TotalCollectionFileSizeUnit and Description for FileArchiveInformation out of then specification.
   string."
  [archive]
  (when-let [spec-string (char-string-value archive "gmd:MD_Format/gmd:specification")]
    (let [archive-map (parsing-util/convert-iso-description-string-to-map spec-string specification-pattern)]
      (when (or (:FormatType archive-map)
                (:FormatDescription archive-map)
                (:AverageFileSize archive-map)
                (:AverageFileSizeUnit archive-map)
                (:TotalCollectionFileSize archive-map)
                (:TotalCollectionFileSizeUnit archive-map)
                (:Description archive-map))
        archive-map))))

(defn parse-archive-info
  "Parses FileArchiveInformation from ISO MENDS and SMAP XML.
   archive-info-xpath is what differentiates between the two, the calling function will pass
   the relevant path."
  [doc archive-info-xpath]
  (for [archive (select doc archive-info-xpath)
        :let [{:keys [FormatType FormatDescription AverageFileSize
                      AverageFileSizeUnit TotalCollectionFileSize
                      TotalCollectionFileSizeUnit Description]} (parse-archive-info-specification archive)]]
    {:Format (char-string-value archive "gmd:MD_Format/gmd:name")
     :FormatType FormatType
     :FormatDescription FormatDescription
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

(def direct-dist-info-instruction-pattern
  (re-pattern "Region:|S3BucketAndObjectPrefixNames:"))

(defn parse-direct-dist-info-instruction
  "Parse all the Region and S3BucketAndObjectPrefixNames for DirectDistributionInformation out of
   the instruction string."
  [distributor]
  (when-let [inst-string (char-string-value
                           distributor
                           (str "gmd:MD_Distributor/gmd:distributionOrderProcess/"
                                "gmd:MD_StandardOrderProcess/gmd:orderingInstructions"))]
    (let [distributor-map (parsing-util/convert-iso-description-string-to-map
                            inst-string
                            direct-dist-info-instruction-pattern)]
      (when (or (:Region distributor-map)
                (:S3BucketAndObjectPrefixNames distributor-map))
        distributor-map))))

(defn parse-direct-dist-info-transfer-options
  "Parse the S3CredentialsAPIEndpoint and S3CredentialsAPIDocumentationURL out of the
   transfer options."
  [distributor]
  (into {}
    (for [transfer-option (select distributor "gmd:MD_Distributor/gmd:distributorTransferOptions")
          :let [[href href-type] (re-matches #"(.*)$" (or (get-in transfer-option [:attrs :xlink/href] "")))]
          :when (string/includes? href-type "DirectDistributionInformation_S3CredentialsAPI")]
      (cond
        (= href-type "DirectDistributionInformation_S3CredentialsAPIEndpoint")
        {:S3CredentialsAPIEndpoint
          (value-of transfer-option
                    "gmd:MD_DigitalTransferOptions/gmd:onLine/gmd:CI_OnlineResource/gmd:linkage/gmd:URL")}
        (= href-type "DirectDistributionInformation_S3CredentialsAPIDocumentationURL")
        {:S3CredentialsAPIDocumentationURL
          (value-of transfer-option
                    "gmd:MD_DigitalTransferOptions/gmd:onLine/gmd:CI_OnlineResource/gmd:linkage/gmd:URL")}))))

(defn parse-direct-dist-info
  "Parses DirectDistributionInformation from ISO MENDS and SMAP XML."
  [doc dist-info-xpath]
  (first
    (for [distributor (select doc (str dist-info-xpath "/gmd:distributor"))
          :let [[href href-type] (re-matches #"(.*)$" (or (get-in distributor [:attrs :xlink/href]) ""))
                {:keys [Region
                        S3BucketAndObjectPrefixNames]}
                (parse-direct-dist-info-instruction distributor)
                {:keys [S3CredentialsAPIEndpoint
                        S3CredentialsAPIDocumentationURL]}
                (parse-direct-dist-info-transfer-options distributor)]
          :when (and (seq href-type)
                     (= href-type "DirectDistributionInformation"))]
      {:Region Region
       :S3BucketAndObjectPrefixNames (when S3BucketAndObjectPrefixNames
                                       (string/split S3BucketAndObjectPrefixNames #" +"))
       :S3CredentialsAPIEndpoint S3CredentialsAPIEndpoint
       :S3CredentialsAPIDocumentationURL S3CredentialsAPIDocumentationURL})))
