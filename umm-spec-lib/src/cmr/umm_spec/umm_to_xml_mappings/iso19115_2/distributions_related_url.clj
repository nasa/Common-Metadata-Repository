(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.distributions-related-url
  "Functions for generating ISO19115-2 XML elements from UMM related-url records."
  (:require [clojure.string :as str]
            [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.common.util :as util]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.organizations-personnel :as org-per]
            [cmr.umm-spec.util :as su]))


(def type->name
  "Mapping of related url type to online resource name"
  {"GET DATA" "DATA ACCESS"
   "VIEW RELATED INFORMATION" "Guide"
   "GET RELATED VISUALIZATION" "Browse"})

(defn browse-url?
  "Returns true if the related-url is browse url"
  [related-url]
  (= "GET RELATED VISUALIZATION" (get-in related-url [:ContentType :Type])))

(defn browse-urls
  "Returns the related-urls that are browse urls"
  [related-urls]
  (filter browse-url? related-urls))

(defn online-resource-urls
  "Returns all related-urls which are not browse urls"
  [related-urls]
  (remove browse-url? related-urls))

(defn generate-browse-urls
  "Returns content generator instructions for a browse url"
  [c]
  (for [{:keys [URLs Description] {:keys [Type]} :ContentType} (browse-urls (:RelatedUrls c))
        url URLs]
    [:gmd:graphicOverview
     [:gmd:MD_BrowseGraphic
      [:gmd:fileName
       [:gmx:FileName {:src url}]]
      [:gmd:fileDescription (char-string Description)]
      [:gmd:fileType (char-string (type->name Type))]]]))

(defn generate-online-resource-url
  "Returns content generator instructions for an online resource url or access url"
  [online-resource-url]
  (let [{:keys [URLs Protocol Description] {:keys [Type]} :ContentType} online-resource-url
        name (type->name Type)
        code (if (= "GET DATA" Type) "download" "information")]
    (for [url URLs]
      [:gmd:onLine
       [:gmd:CI_OnlineResource
        [:gmd:linkage
         [:gmd:URL url]]
        [:gmd:protocol
         (char-string Protocol)]
        [:gmd:name
         (char-string name)]
        (if Description
          [:gmd:description
           (char-string Description)]
          [:gmd:description {:gco:nilReason "missing"}])
        [:gmd:function
         [:gmd:CI_OnLineFunctionCode
          {:codeList (str (:ngdc iso/code-lists) "#CI_OnLineFunctionCode")
           :codeListValue code}]]]])))

(defn generate-distributions
  "Returns content generator instructions for distributions in the given umm-c"
  [c]
  (let [distributions (su/remove-empty-records (:Distributions c))
        related-urls (online-resource-urls (:RelatedUrls c))]
    (when (or distributions related-urls)
      (let [truncate-map (fn [key] (util/truncate-nils (map key distributions)))
            sizes (truncate-map :DistributionSize)
            fees (truncate-map :Fees)]
        [:gmd:distributionInfo
         [:gmd:MD_Distribution
          [:gmd:distributor
           [:gmd:MD_Distributor
            [:gmd:distributorContact {:gco:nilReason "missing"}
             (when-let [responsibility (first (org-per/responsibility-by-role (:Organizations c) "DISTRIBUTOR"))]
               (org-per/generate-responsible-party responsibility))]
            (for [fee (map su/nil-to-empty-string fees)]
              [:gmd:distributionOrderProcess
               [:gmd:MD_StandardOrderProcess
                [:gmd:fees
                 (char-string fee)]]])
            (for [distribution distributions
                  :let [{media :DistributionMedia format :DistributionFormat} distribution]]
              [:gmd:distributorFormat
               [:gmd:MD_Format
                [:gmd:name
                 (char-string (su/nil-to-empty-string format))]
                [:gmd:version {:gco:nilReason "unknown"}]
                [:gmd:specification
                 (char-string (su/nil-to-empty-string media))]]])
            (for [size sizes]
              [:gmd:distributorTransferOptions
               [:gmd:MD_DigitalTransferOptions
                ;; size could be a number or string, so the checking here is verbose
                (if (or (nil? size)
                        (and (string? size) (str/blank? size)))
                  ;; we have to generate an empty element for Distribution Size with nil.
                  ""
                  [:gmd:transferSize
                   [:gco:Real size]])]])
            [:gmd:distributorTransferOptions
             [:gmd:MD_DigitalTransferOptions
              (for [related-url related-urls]
                (generate-online-resource-url related-url))]]]]]]))))
