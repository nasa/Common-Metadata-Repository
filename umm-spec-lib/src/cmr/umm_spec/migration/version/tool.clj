(ns cmr.umm-spec.migration.version.tool
  "Contains functions for migrating between versions of the UMM tool schema."
  (:require
   [clojure.string :as string]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.version.interface :as interface]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;;; tool Migration Implementations

(defn- convert-related-url-1_1->1_1_1
  "Convert RelatedURL from 1.1 to 1.1.1"
  [related-url]
  ;;The following values are migrated without Subtype
  ;;because they don't exist in 1.1.1(kms)
  ;;"CollectionURL" "DATA SET LANDING PAGE" 
  ;;"CollectionURL" "EXTENDED METADATA"
  ;;"CollectionURL" "PROFESSIONAL HOME PAGE"
  ;;"CollectionURL" "PROJECT HOME PAGE"
  ;;"PublicationURL" "VIEW RELATED INFORMATION" ["GIOVANNI" "MAP" "WORLDVIEW"] 
  ;;"VisualizationURL" "GET RELATED VISUALIZATION" [other than "GIOVANNI" "MAP" "WORLDVIEW"]

  ;;The following values are migrated to "PublicationURL" "VIEW RELATED INFORMATION"
  ;;because Type don't exist in 1.1.1(kms)
  ;;"CollectionURL" "VIEW RELATED INFORMATION"
  ;;"CollectionURL" "GET RELATED VISUALIZATION"
  ;;"CollectionURL" "BROWSE"
  ;;"PublicationURL" [other than "VIEW RELATED INFORMATION"]
  ;;"VisualizationURL" [other than "GET RELATED VISUALIZATION"]

  (let [uct (:URLContentType related-url) 
        t (:Type related-url) 
        st (:Subtype related-url)]
    (if (or (and (= "CollectionURL" uct)
                 (some #(= t %)
                  ["DATA SET LANDING PAGE" "EXTENDED METADATA" "PROFESSIONAL HOME PAGE" "PROJECT HOME PAGE"])) 
            (and (= "PublicationURL" uct)
                 (= "VIEW RELATED INFORMATION" t)
                 (some #(= st %) ["GIOVANNI" "MAP" "WORLDVIEW"]))
            (and (= "VisualizationURL" uct)
                 (= "GET RELATED VISUALIZATION" t)
                 (not (some #(= st %) ["GIOVANNI" "MAP" "WORLDVIEW"]))))
      (dissoc related-url :Subtype)
      (if (or (and (= "CollectionURL" uct)
                   (some #(= t %)
                    ["VIEW RELATED INFORMATION" "GET RELATED VISUALIZATION" "BROWSE"]))
              (and (= "PublicationURL" uct)
                   (not= "VIEW RELATED INFORMATION" t))
              (and (= "VisualizationURL" uct)
                   (not= "GET RELATED VISUALIZATION" t)))
        (-> related-url
            (assoc :URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION")
            (dissoc :Subtype))
        related-url)))) 

(defn- convert-related-url-1_1_1->1_1
  "Convert RelatedURL from v1.1.1 to v1.1."
  [related-url]
  ;;The following values are migrated without Subtype
  ;;because they don't exist in 1.1
  ;;"CollectionURL" "EXTENDED METADATA" ["DMR++ MISSING DATA" "DMR++"]
  ;;"PublicationURL" "VIEW RELATED INFORMATION" "DATA PRODUCT SPECIFICATION"
  ;;"VisualizationURL" "GET RELATED VISUALIZATION" "SOTO"
  
  ;; The following values are migrated to "VisualizationURL" "GET RELATED VISUALIZATION"
  ;; because Type doesn't exist in 1.1
  ;;"VisualizationURL" "Color Map" [any valid 1.1.1 Subtype]

  ;; The following values are migrated to "PublicationURL" "VIEW RELATED INFORMATION" 
  ;;[other than "CollectionURL" "PublicationURL" "VisualizationURL"]

  (let [uct (:URLContentType related-url)
        t (:Type related-url)
        st (:Subtype related-url)]
    (if (or (and (= "CollectionURL" uct)
                 (= "EXTENDED METADATA" t)
                 (some #(= st %) ["DMR++ MISSING DATA" "DMR++"]))
            (and (= "PublicationURL" uct)
                 (= "VIEW RELATED INFORMATION" t)
                 (= "DATA PRODUCT SPECIFICATION" st))
            (and (= "VisualizationURL" uct)
                 (= "GET RELATED VISUALIZATION" t)
                 (= "SOTO" st)))
      (dissoc related-url :Subtype)
      (if (and (= "VisualizationURL" uct)
               (= "Color Map" t))
        (-> related-url
            (assoc :URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION")
            (dissoc :Subtype))
        (if (not (some #(= uct %) ["CollectionURL" "PublicationURL" "VisualizationURL"]))
          (-> related-url
            (assoc :URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION")
            (dissoc :Subtype))
          related-url)))))

(defn- migrate-related-urls-1_1->1_1_1
  "Migrate RelatedURLs from 1.1 to 1.1.1"
  [tool]
  (if (:RelatedURLs tool)
    (assoc tool :RelatedURLs (map convert-related-url-1_1->1_1_1 (:RelatedURLs tool)))
    tool))

(defn- migrate-related-urls-1_1_1->1_1
  "Migrate RelatedURLs from 1.1.1 to 1.1"
  [tool]
  (if (:RelatedURLs tool)
    (assoc tool :RelatedURLs (map convert-related-url-1_1_1->1_1 (:RelatedURLs tool)))
    tool))

(defn- migrate-url-1_1->1_1_1
  "migrate URL from 1.1 to 1.1.1"
  [tool]
  ;;"DistributionURL" "GOTO WEB TOOL" "MOBILE APP" -> "DistributionURL" "GOTO WEB TOOL" 
  ;;"DistributionURL" "DOWNLOAD SOFTWARE" ["LIVE ACCESS SERVER (LAS)" "MAP VIEWER" "SIMPLE SUBSET WIZARD (SSW)" "SUBSETTER"]
  ;;-> "DistributionURL" "DOWNLOAD SOFTWARE" 
  (let [subtype-list ["LIVE ACCESS SERVER (LAS)" "MAP VIEWER" "SIMPLE SUBSET WIZARD (SSW)" "SUBSETTER"]
        uct (get-in tool [:URL :URLContentType])
        t (get-in tool [:URL :Type])
        st (get-in tool [:URL :Subtype])]
    (if (or (and (= "DistributionURL" uct) (= "GOTO WEB TOOL" t) (= "MOBILE APP" st))
            (and (= "DistributionURL" uct) (= "DOWNLOAD SOFTWARE" t) (some #(= st %) subtype-list)))
      (update-in tool [:URL] dissoc :Subtype)
      tool))) 

(defn- migrate-url-1_1_1->1_1
  "Migrate URL from 1.1.1 to 1.1"
  [tool]
  ;; URLContentType needs to be "DistributionURL"
  ;; Type needs to be in ["GOTO WEB TOOL", "DOWNLOAD SOFTWARE"] or "GOTO WEB TOOL"
  ;; Subtype needs to be in the following list or remove it.
  ;; ["MOBILE APP", "LIVE ACCESS SERVER (LAS)", "MAP VIEWER", "SIMPLE SUBSET WIZARD (SSW)", "SUBSETTER"]
  (let [type-list ["GOTO WEB TOOL" "DOWNLOAD SOFTWARE"]
        subtype-list ["MOBILE APP"
                      "LIVE ACCESS SERVER (LAS)"
                      "MAP VIEWER"
                      "SIMPLE SUBSET WIZARD (SSW)"
                      "SUBSETTER"]]
    (as-> tool t
          (assoc-in t [:URL :URLContentType] "DistributionURL")
          (if-not (some #(= (get-in t [:URL :Type]) %) type-list)
            (assoc-in t [:URL :Type] "GOTO WEB TOOL")
            t)
          (if-not (some #(= (get-in t [:URL :Subtype]) %) subtype-list)
            (update-in t [:URL] dissoc :Subtype)
            t)))) 

(defn migrate-related-urls-1_2_0->1_1_1
  "Migrate RelatedURLs from 1.2.0 to 1.1.1"
  [tool]
  ;; Remove Format and MimeType from each entry in RelatedURLs.
  (if-let [related-urls (:RelatedURLs tool)]
    (assoc tool :RelatedURLs (map #(dissoc % :Format :MimeType) related-urls))
    tool)) 
 
(defmethod interface/migrate-umm-version [:tool "1.0" "1.1"]
  [context t & _]
  (-> t
      (dissoc :SearchAction)
      (m-spec/update-version :tool "1.1")))

(defmethod interface/migrate-umm-version [:tool "1.1" "1.0"]
  [context t & _]
  (-> t
      (dissoc :PotentialAction)
      (m-spec/update-version :tool "1.0")))

(defmethod interface/migrate-umm-version [:tool "1.1" "1.1.1"]
  [context t & _]
  (-> t
      (migrate-related-urls-1_1->1_1_1)
      (migrate-url-1_1->1_1_1)
      (m-spec/update-version :tool "1.1.1")))

(defmethod interface/migrate-umm-version [:tool "1.1.1" "1.1"]
  [context t & _]
  (-> t
      (migrate-related-urls-1_1_1->1_1)
      (migrate-url-1_1_1->1_1)
      (m-spec/update-version :tool "1.1")))

(defmethod interface/migrate-umm-version [:tool "1.1.1" "1.2.0"]
  [context t & _]
  (m-spec/update-version t :tool "1.2.0"))

(defmethod interface/migrate-umm-version [:tool "1.2.0" "1.1.1"]
  [context t & _]
  (-> t
      (migrate-related-urls-1_2_0->1_1_1) 
      (m-spec/update-version :tool "1.1.1")))
