(ns cmr.umm-spec.migration.version.tool
  "Contains functions for migrating between versions of the UMM tool schema."
  (:require
   [clojure.string :as string]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.version.interface :as interface]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;;; tool Migration Implementations

(def valid-urlcontenttype-type-subType-combo-in-relatedurl-1_1
  "Valid RelatedURL fields combo in version 1.1"
  ["CollectionURL,DATA SET LANDING PAGE"
   "CollectionURL,EXTENDED METADATA"
   "CollectionURL,PROFESSIONAL HOME PAGE"
   "CollectionURL,PROJECT HOME PAGE"
   "PublicationURL,VIEW RELATED INFORMATION,ALGORITHM DOCUMENTATION"
   "PublicationURL,VIEW RELATED INFORMATION,ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
   "PublicationURL,VIEW RELATED INFORMATION,ANOMALIES"
   "PublicationURL,VIEW RELATED INFORMATION,CASE STUDY"
   "PublicationURL,VIEW RELATED INFORMATION,DATA CITATION POLICY"
   "PublicationURL,VIEW RELATED INFORMATION,DATA QUALITY"
   "PublicationURL,VIEW RELATED INFORMATION,DATA RECIPE"
   "PublicationURL,VIEW RELATED INFORMATION,DELIVERABLES CHECKLIST"
   "PublicationURL,VIEW RELATED INFORMATION,GENERAL DOCUMENTATION"
   "PublicationURL,VIEW RELATED INFORMATION,HOW-TO"
   "PublicationURL,VIEW RELATED INFORMATION,IMPORTANT NOTICE"
   "PublicationURL,VIEW RELATED INFORMATION,INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"
   "PublicationURL,VIEW RELATED INFORMATION,MICRO ARTICLE"
   "PublicationURL,VIEW RELATED INFORMATION,PI DOCUMENTATION"
   "PublicationURL,VIEW RELATED INFORMATION,PROCESSING HISTORY"
   "PublicationURL,VIEW RELATED INFORMATION,PRODUCT HISTORY"
   "PublicationURL,VIEW RELATED INFORMATION,PRODUCT QUALITY ASSESSMENT"
   "PublicationURL,VIEW RELATED INFORMATION,PRODUCT USAGE"
   "PublicationURL,VIEW RELATED INFORMATION,PRODUCTION HISTORY"
   "PublicationURL,VIEW RELATED INFORMATION,PUBLICATIONS"
   "PublicationURL,VIEW RELATED INFORMATION,READ-ME"
   "PublicationURL,VIEW RELATED INFORMATION,REQUIREMENTS AND DESIGN"
   "PublicationURL,VIEW RELATED INFORMATION,SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"
   "PublicationURL,VIEW RELATED INFORMATION,SCIENCE DATA PRODUCT VALIDATION"
   "PublicationURL,VIEW RELATED INFORMATION,USER FEEDBACK PAGE"
   "PublicationURL,VIEW RELATED INFORMATION,USER'S GUIDE"
   "PublicationURL,VIEW RELATED INFORMATION"
   "VisualizationURL,GET RELATED VISUALIZATION,GIOVANNI"
   "VisualizationURL,GET RELATED VISUALIZATION,MAP"
   "VisualizationURL,GET RELATED VISUALIZATION,WORLDVIEW"
   "VisualizationURL,GET RELATED VISUALIZATION"
   "VisualizationURL,BROWSE,GIOVANNI"
   "VisualizationURL,BROWSE,MAP"
   "VisualizationURL,BROWSE,WORLDVIEW"
   "VisualizationURL,BROWSE"])
 
(defn- migrate-related-urls-1_1->1_1_1
  "Migrate RelatedURLs from 1.1 to 1.1.1"
  [tool]
  ;; "BROWSE" doesn't exist in kms, change it to
  ;; "GET RELATED VISUALIZATION"
  (if (:RelatedURLs tool)
    (assoc tool :RelatedURLs (map #(if (= "BROWSE" (:Type %))
                                     (assoc % :Type "GET RELATED VISUALIZATION")
                                     %)
                                  (:RelatedURLs tool)))
    tool))

(defn- convert-related-url-1_1_1->1_1
  "Convert related url from v1.1.1 to v1.1."
  [related-url]
  (let [uctype (:URLContentType related-url)
        type (:Type related-url)
        stype (:Subtype related-url)
        u-t-s-combo (if stype
                      (str uctype "," type "," stype)
                      (str uctype "," type))
        u-t-combo (str uctype "," type)]
    (if (some #(= u-t-s-combo %) valid-urlcontenttype-type-subType-combo-in-relatedurl-1_1)
      related-url
      (if (some #(= u-t-combo %) valid-urlcontenttype-type-subType-combo-in-relatedurl-1_1) 
        (dissoc related-url :Subtype)
        (let [valid-combo (some #(when (or (string/includes? % uctype)
                                           (string/includes? % "PublicationURL"))
                                   %)
                                valid-urlcontenttype-type-subType-combo-in-relatedurl-1_1)
              u-t-s (string/split valid-combo #",")]
          ;;if uctype is CollectionURL, u-t-s doesn't contain Subtype.
          (if (= uctype "CollectionURL")
            (-> related-url
                (assoc :URLContentType (first u-t-s))
                (assoc :Type (second u-t-s))
                (dissoc :Subtype))
            (-> related-url
                (assoc :URLContentType (first u-t-s))
                (assoc :Type (second u-t-s))
                (assoc :Subtype (last u-t-s)))))))))

(defn- migrate-related-urls-1_1_1->1_1
  "Migrate RelatedURLs from 1.1.1 to 1.1"
  [tool]
  (if (:RelatedURLs tool)
    (assoc tool :RelatedURLs (map convert-related-url-1_1_1->1_1 (:RelatedURLs tool)))
    tool))

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
      (m-spec/update-version :tool "1.1.1")))

(defmethod interface/migrate-umm-version [:tool "1.1.1" "1.1"]
  [context t & _]
  (-> t
      (migrate-related-urls-1_1_1->1_1)
      (migrate-url-1_1_1->1_1)
      (m-spec/update-version :tool "1.1")))
