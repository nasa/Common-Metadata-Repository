(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url
  "Functions for parsing UMM related-url records out of ISO XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.opendap-util :as opendap-util]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as iso-parsing-util]))

(def size-of-related-url-fees
  "This constant is the size of the fees element in the RelatedURL main element for UMM-C. It is
   needed to be able to truncate strings that are too long."
  80)

(def description-string-field-re-pattern
  "Returns the pattern that matches all the related fields in description-string"
  (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:"))

(defn parse-url-types-from-description
 "In ISO, since there are not separate fields for the types, they are put in the
 description in the format 'Description: X URLContentType: Y Type: Z Subtype: A'
 Parse all available elements out from the description string."
 [description]
 (when description
   (let [description-map
          (iso-parsing-util/convert-iso-description-string-to-map description
                                                                  description-string-field-re-pattern)]
    (if (empty? description-map)
      {:Description description} ;;Description not formatted like above, so just description
      description-map))))

(defn- parse-operation-description
  "Parses operationDescription string, returns MimeType, DataID, and DataType"
  [operation-description]
  (when operation-description
    (iso-parsing-util/convert-iso-description-string-to-map
      operation-description
      (re-pattern "MimeType:|DataID:|DataType:|Format:"))))

(defn parse-service-urls
  "Parse service URLs from service location. These are most likely dups of the
  distribution urls, but may contain additional type info."
  [doc sanitize? service-url-path service-online-resource-xpath]
  (for [service (select doc service-url-path)
        :let [local-name (value-of service "srv:serviceType/gco:LocalName")]
        :when (str/includes? local-name "RelatedURL")
        :let [url-types (parse-url-types-from-description local-name)
              uris (mapv #(value-of % "gmd:linkage/gmd:URL")
                         (select service service-online-resource-xpath))
              url (first (select service service-online-resource-xpath))
              url-link (value-of url "gmd:linkage/gmd:URL")
              full-name (value-of service "srv:containsOperations/srv:SV_OperationMetadata/srv:operationName/gco:CharacterString")
              protocol (value-of url "gmd:protocol/gco:CharacterString")
              description (char-string-value url "gmd:description")
              ;;http is currently invalid in the schema, use HTTP instead
              protocol (if (= "http" protocol)
                         "HTTP"
                         protocol)
              operation-description (value-of service "srv:containsOperations/srv:SV_OperationMetadata/srv:operationDescription/gco:CharacterString")
              {:keys [MimeType DataID DataType Format]} (parse-operation-description operation-description)]]
    (merge url-types
           {:URL (when url-link (url/format-url url-link sanitize?))
            :Description (when (seq description)
                           (str/trim description))}
           (util/remove-nil-keys
            {:GetService (when (or MimeType full-name DataID protocol DataType Format
                                  (not (empty? uris)))
                           {:MimeType (su/with-default MimeType sanitize?)
                            :FullName (su/with-default full-name sanitize?)
                            :DataID (su/with-default DataID sanitize?)
                            :Protocol (su/with-default protocol sanitize?)
                            :DataType (su/with-default DataType sanitize?)
                            :Format Format
                            :URI (when-not (empty? uris)
                                   uris)})}))))

(defn- parse-distributor-format
  "Parses distributor format string, returns MimeType and Format"
  [distributor distributor-xpaths-map]
  (when-let [format-name (value-of distributor (get distributor-xpaths-map :Format))]
    (iso-parsing-util/convert-iso-description-string-to-map format-name
                                                            (re-pattern "MimeType:|Format:"))))

(defn parse-online-urls
  "Parse ISO online resource urls. There are several places in ISO that allow for multiples of'
   online urls."
  [doc sanitize? service-urls distributor-xpaths-map]
  (for [distributor (select doc (get distributor-xpaths-map :Root))
        :let [fees (util/trunc (value-of distributor (get distributor-xpaths-map :Fees)) size-of-related-url-fees)
              format (parse-distributor-format distributor distributor-xpaths-map)
              [href href-type] (re-matches #"(.*)$" (or (get-in distributor [:attrs :xlink/href]) ""))]
        transfer-option (select distributor (get distributor-xpaths-map :TransferOptions))
        :let [size (value-of transfer-option "gmd:transferSize")
              unit (value-of transfer-option "gmd:unitsOfDistribution/gco:CharacterString")]
        url (select transfer-option (get distributor-xpaths-map :URL))
        :let [name (char-string-value url "gmd:name")
              code (value-of url "gmd:function/gmd:CI_OnlineFunctionCode")
              url-link (value-of url "gmd:linkage/gmd:URL")
              url-link (when url-link (url/format-url url-link sanitize?))
              opendap-type (when (= code "GET DATA : OPENDAP DATA (DODS)")
                             opendap-util/opendap-url-type-str)
              types-and-desc (parse-url-types-from-description
                              (char-string-value url "gmd:description"))
              service-url (first (filter #(= url-link (:URL %)) service-urls))
              type (or opendap-type (:Type types-and-desc) (:Type service-url) "GET DATA")]
        :when (not (= href-type "DirectDistributionInformation"))]
    (merge
     {:URL url-link
      :URLContentType (or (:URLContentType types-and-desc)
                          "DistributionURL")
      :Type type
      :Subtype (if opendap-type
                "OPENDAP DATA (DODS)"
                (or (:Subtype types-and-desc) (:Subtype service-url)))
      :Description (:Description types-and-desc)}
     (case type
       (or "GET DATA"
           "GET CAPABILITIES") {:GetData {:Format (su/with-default (:Format format) sanitize?)
                                          :Size (if sanitize?
                                                  (or size 0.0)
                                                  size)
                                          :Unit (if sanitize?
                                                  (or unit "KB")
                                                  unit)
                                          :Fees fees
                                          :Checksum (:Checksum types-and-desc)
                                          :MimeType (:MimeType format)}}
       nil))))

(defn parse-online-and-service-urls
  "Parse online and service urls. Service urls may be a dup of distribution urls,
  but may contain additional needed type information. Filter out dup service URLs."
  [doc sanitize? service-url-path distributor-xpaths-map service-online-resource-xpath]
  (let [service-urls (parse-service-urls doc sanitize? service-url-path service-online-resource-xpath)
        online-urls (parse-online-urls doc sanitize? service-urls distributor-xpaths-map)
        online-url-urls (set (map :URL online-urls))]
    (concat
     online-urls
     service-urls)))

(defn parse-browse-graphics
  "Parse browse graphic urls"
  [doc sanitize? browse-graphic-xpath]
  (for [url (select doc browse-graphic-xpath)
        ;; We retrieve browse url from two different places. This might change depending on the
        ;; outcome of ECSE-129.
        :let [browse-url (or (value-of url "gmd:fileName/gmx:FileName/@src")
                             (value-of url "gmd:fileName/gco:CharacterString"))
              types-and-desc (parse-url-types-from-description
                              (char-string-value url "gmd:fileDescription"))]]
     {:URL (when browse-url (url/format-url browse-url sanitize?))
      :Description (:Description types-and-desc)
      :URLContentType "VisualizationURL"
      :Type "GET RELATED VISUALIZATION"
      :Subtype (:Subtype types-and-desc)}))

(defn parse-publication-urls
 "Parse PublicationURL and CollectionURL from the publication location."
 [doc sanitize? publication-url-path]
 (for [url (select doc publication-url-path)
       :let [description (char-string-value url "gmd:description")]
       :when (and (some? description)
                  (not (str/includes? description "PublicationReference:")))
       :let [types-and-desc (parse-url-types-from-description description)
             url-content-type (or (:URLContentType types-and-desc) "PublicationURL")]]
  {:URL (value-of url "gmd:linkage/gmd:URL")
   :Description (:Description types-and-desc)
   :URLContentType (or (:URLContentType types-and-desc) "PublicationURL")
   ;; Types are validated against the URLContentType, so if URLContentType is
   ;; CollectionURL then use the valid DATA SET LANDING PAGE type, otherwise use
   ;; VIEW RELATED INFORMATION which is valid for PublicationURL
   :Type (or (:Type types-and-desc) (if (= (:URLContentType types-and-desc) "CollectionURL")
                                      "DATA SET LANDING PAGE"
                                      "VIEW RELATED INFORMATION"))
   :Subtype (:Subtype types-and-desc)}))
