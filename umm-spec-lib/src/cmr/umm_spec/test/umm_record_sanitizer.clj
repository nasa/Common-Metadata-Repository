(ns cmr.umm-spec.test.umm-record-sanitizer
  "This contains functions for manipulating the generator generated umm-record to a sanitized
  version to pass the xml validation for various supported metadata format. This is needed because
  the incompatibility between UMM JSON schema and schemas of the various metadata formats making the
  generated metadata xml invalid without some kind of sanitization."
  (:require
   [clojure.string :as string]
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as test]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.umm-spec.models.umm-tool-models :as umm-t]
   [cmr.umm-spec.util :as spec-util]))

(defn- set-if-exist
  "Sets the field of the given record to the value if the field has a value, returns the record."
  [record field value]
  (if (field record)
    (assoc record field value)
    record))

(defn- sanitize-science-keywords
  "Temporary! We should be able to define the JSON schema in a way that ensures science keyword
  hierarchy is obeyed. It could potentially be done using a complex oneOf or anyOf."
  [record]
  (assoc record
         :ScienceKeywords (seq (for [sk (:ScienceKeywords record)]
                                 (cond
                                   (nil? (:VariableLevel1 sk))
                                   (assoc sk
                                          :VariableLevel2 nil
                                          :VariableLevel3 nil
                                          :DetailedVariable nil)

                                   (nil? (:VariableLevel2 sk))
                                   (assoc sk :VariableLevel3 nil)

                                   :else
                                   sk)))))

(def valid-url-content-types-map-service-version-1-3
  {"DistributionURL" {"GET DATA" ["DATACAST URL"
                                  "EARTHDATA SEARCH"
                                  "ECHO"
                                  "EDG"
                                  "EOSDIS DATA POOL"
                                  "GDS"
                                  "GIOVANNI"
                                  "KML"
                                  "LAADS"
                                  "LANCE"
                                  "LAS"
                                  "MIRADOR"
                                  "MODAPS"
                                  "NOAA CLASS"
                                  "ON-LINE ARCHIVE"
                                  "REVERB"]
                      "GET SERVICE" ["ACCESS MAP VIEWER"
                                     "ACCESS MOBILE APP"
                                     "ACCESS WEB SERVICE"
                                     "DIF"
                                     "MAP SERVICE"
                                     "NOMADS"
                                     "OPENDAP DATA"
                                     "OPENDAP DATA (DODS)"
                                     "OPENDAP DIRECTORY (DODS)"
                                     "OpenSearch"
                                     "SERF"
                                     "SOFTWARE PACKAGE"
                                     "SSW"
                                     "SUBSETTER"
                                     "THREDDS CATALOG"
                                     "THREDDS DATA"
                                     "THREDDS DIRECTORY"
                                     "WEB COVERAGE SERVICE (WCS)"
                                     "WEB FEATURE SERVICE (WFS)"
                                     "WEB MAP FOR TIME SERIES"
                                     "WEB MAP SERVICE (WMS)"
                                     "WORKFLOW (SERVICE CHAIN)"]}
   "VisualizationURL" {"GET RELATED VISUALIZATION" ["GIBS" "GIOVANNI" "MAP"]}
   "CollectionURL" {"DATA SET LANDING PAGE" []
                    "DOI" []
                    "EXTENDED METADATA" []
                    "PROFESSIONAL HOME PAGE" []
                    "PROJECT HOME PAGE" []}
   "PublicationURL" {"VIEW RELATED INFORMATION" ["ALGORITHM THEORETICAL BASIS DOCUMENT"
                                                 "CALIBRATION DATA DOCUMENTATION"
                                                 "CASE STUDY"
                                                 "DATA QUALITY"
                                                 "DATA USAGE"
                                                 "DELIVERABLES CHECKLIST"
                                                 "GENERAL DOCUMENTATION"
                                                 "HOW-TO"
                                                 "PI DOCUMENTATION"
                                                 "PROCESSING HISTORY"
                                                 "PRODUCTION VERSION HISTORY"
                                                 "PRODUCT QUALITY ASSESSMENT"
                                                 "PRODUCT USAGE"
                                                 "PRODUCT HISTORY"
                                                 "PUBLICATIONS"
                                                 "RADIOMETRIC AND GEOMETRIC CALIBRATION METHODS"
                                                 "READ-ME"
                                                 "RECIPE"
                                                 "REQUIREMENTS AND DESIGN"
                                                 "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"
                                                 "SCIENCE DATA PRODUCT VALIDATION"
                                                 "USER FEEDBACK"
                                                 "USER'S GUIDE"]}
   "DataCenterURL" {"HOME PAGE" []}
   "DataContactURL" {"HOME PAGE" []}})

(defn- valid-types-for-url-content-type
  "Returns all valid Types for URLContentType"
  [url-content-type concept-type]
  (case concept-type
    :collection (keys (get spec-util/valid-url-content-types-map url-content-type))
    :service (keys (get valid-url-content-types-map-service-version-1-3 url-content-type))
    (assert nil (str "The RelatedUrl URLContentType, Type, and Subtype valid values are not
                      defined for the " concept-type " concept-type."))))

(defn- valid-subtypes-for-type
  "Returns all Subtypes for URLContentType/Type combination"
  [url-content-type type concept-type]
  (case concept-type
    :collection (get-in spec-util/valid-url-content-types-map [url-content-type type])
    :service (get-in valid-url-content-types-map-service-version-1-3 [url-content-type type])
    (assert nil (str "The RelatedUrl URLContentType, Type, and Subtype valid values are not
                      defined for the " concept-type " concept-type."))))

(defn- generate-valid-type-and-subtype-for-url-content-type
 "Generate a valid URLContentType, Type, and Subtype combo given the URLContentType.
 when you can't get type and subtype from URLContentType, create a valid combo."
 [url-content-type concept-type]
 (let [valid-types (valid-types-for-url-content-type url-content-type concept-type)
       url-content-type (if valid-types
                          url-content-type
                          "DataCenterURL")
       valid-types (or valid-types (valid-types-for-url-content-type url-content-type concept-type))
       types (when (seq valid-types) (gen/sample (gen/elements valid-types) 1))
       valid-subtypes (valid-subtypes-for-type url-content-type (first types) concept-type)
       subtypes (when (seq valid-subtypes) (gen/sample (gen/elements valid-subtypes) 1))]
  {:URLContentType url-content-type
   :Type (first types)
   :Subtype (first subtypes)}))

(defn- sanitize-get-data
  "Removes GetData from relate-url if URLContentType != DistributionURL
   and Type != GET DATA"
  [related-url]
  (if (and (= (:Type related-url) "GET DATA") (= "DistributionURL" (:URLContentType related-url)))
    (dissoc related-url :GetService)
    (dissoc related-url :GetData :GetService)))

(defn- sanitize-get-service
  "Removes GetService from relate-url if URLContentType != DistributionURL
   and Type != GET SERVICE"
  [related-url]
  (if (and (= (:Type related-url) "GET SERVICE") (= "DistributionURL" (:URLContentType related-url)))
    (dissoc related-url :GetData)
    (dissoc related-url :GetService :GetData)))

(defn- sanitize-get-service-and-get-data
  "For any URLContentType and Type combination that shouldn't have GetService or GetData fields,
   the fields are removed and the related-url is returned"
  [related-url]
  (-> related-url
      sanitize-get-service
      sanitize-get-data))

(defn- sanitize-related-urls
  "Returns a list of sanitized related urls"
  [related-urls concept-type]
  (when related-urls
   (for [ru related-urls
         :let [urlcontenttype-type-subtype
               (generate-valid-type-and-subtype-for-url-content-type (:URLContentType ru) concept-type)]]
    (merge
     (-> ru
         sanitize-get-service-and-get-data
         (assoc :URL (first (gen/sample test/file-url-string 1))))
     urlcontenttype-type-subtype))))

(defn- sanitize-contact-informations
  "Sanitize a record with ContactInformation"
  [records concept-type]
  (seq (for [record records]
         (if-let [contact-info (:ContactInformation record)]
           (update-in record
                      [:ContactInformation :RelatedUrls]
                      #(sanitize-related-urls % concept-type))
           record))))

(defn- sanitize-data-centers
  "Sanitize data center contact information and the contact information on contact
  persons and groups associated with the data center"
  [data-centers concept-type]
  (seq (for [dc (sanitize-contact-informations data-centers concept-type)]
         (-> dc
             (update :ContactPersons #(sanitize-contact-informations % concept-type))
             (update :ContactGroups #(sanitize-contact-informations % concept-type))))))

(defn- sanitize-umm-record-related-urls
  "Sanitize the RelatedUrls on the collection if any exist"
  [record concept-type]
  (if (seq (:RelatedUrls record))
    (update record :RelatedUrls #(sanitize-related-urls % concept-type))
    record))

(defn- sanitize-umm-record-data-centers
  "Sanitize data centers if there are any data centers in the record"
  [record concept-type]
  (if (seq (:DataCenters record))
    (update record :DataCenters #(sanitize-data-centers % concept-type))
    record))

(defn- sanitize-umm-record-contacts
  "Sanitize RelatedUrls if they key exists."
  [record key concept-type]
  (if (seq (get record key))
    (update record key #(sanitize-contact-informations % concept-type))
    record))

(defn- sanitize-online-resource
  "Sanitize the OnlineResource Linkage (url)"
  [c]
  (if (get-in c [:OnlineResource :Linkage])
   (assoc-in c [:OnlineResource :Linkage] (first (gen/sample test/file-url-string 1)))
   c))

;; We should consolidate and simplify this into defmulti for the url-content-type mappings.
(defn- sanitize-umm-record-urls
  "Sanitize all RelatedUrls throughout the collection by making them url strings."
  [record concept-type]
  (-> record
      (sanitize-umm-record-related-urls concept-type)
      (sanitize-umm-record-data-centers concept-type)
      (sanitize-umm-record-contacts :ContactPersons concept-type)
      (sanitize-umm-record-contacts :ContactGroups concept-type)))

(defn- sanitize-umm-number-of-instruments
  "Sanitize all the instruments with the :NumberOfInstruments for its child instruments"
  [record]
  (-> record
      (update-in-each [:Platforms] update-in-each [:Instruments]
        #(assoc % :NumberOfInstruments (let [ct (count (:ComposedOf %))]
                                         (when (> ct 0) ct))))))

(def valid-uri
  "http://google.com")

(defn- sanitize-umm-data-presentation-form
  "UMM-C schema only requires it as a string, but xml schema requires it as anyURI.
   Replace it with a valid URI."
  [record]
  (-> record
      (update-in-each [:CollectionCitations]
        #(assoc % :DataPresentationForm (when (:DataPresentationForm %)
                                          valid-uri)))))

(defn- sanitize-umm-file-distribution-media
  "UMM-C schema only requires it as a string, but xml schema requires it as anyURI.
   Replace it with a valid URI."
  [record]
  (if (seq (get-in record [:ArchiveAndDistributionInformation :FileDistributionInformation]))
    (-> record
        (update-in-each [:ArchiveAndDistributionInformation :FileDistributionInformation]
          #(assoc % :Media (when (:Media %)
                             ["Online"]))))
    record))

(defn- sanitize-umm-online-resource-function
  "UMM-C schema only requires it as a string, but xml schema requires it as anyURI.
   Replace it with a valid URI."
  [record]
  (if (get-in record [:OnlineResource :Function])
   (assoc-in record [:OnlineResource :Function] valid-uri)
   record))

(defn- sanitize-umm-collection-citations
  "Sanitize different elements of the CollectionCitations data."
  [record]
  (-> record
      (update-in-each [:CollectionCitations] sanitize-online-resource)
      (update-in-each [:CollectionCitations] sanitize-umm-online-resource-function)))

(defn- sanitize-umm-use-constraints
  "Sanitize UseConstraints data.
   Even though the schema will fail the validation
   when both LicenseUrl and LicenseText are present,
   somehow it still gets generated."
  [record]
  (if (and (get-in record [:UseConstraints :LicenseURL])
           (get-in record [:UseConstraints :LicenseText]))
    (assoc-in record [:UseConstraints :LicenseText] nil)
    record))

(defn sanitized-umm-c-record
  "Returns the sanitized version of the given umm-c record."
  [record]
  (-> record
      sanitize-umm-use-constraints
      ;; DataLanguage should be from a list of enumerations which are not defined in UMM JSON schema
      ;; so here we just replace the generated value to eng to make it through the validation.
      (set-if-exist :DataLanguage "eng")
      (set-if-exist :CollectionProgress "COMPLETE")
      ;; Figure out if we can define this in the schema
      sanitize-science-keywords
      (sanitize-umm-record-urls :collection)
      (update-in-each [:PublicationReferences] sanitize-online-resource)
      sanitize-umm-number-of-instruments
      sanitize-umm-data-presentation-form
      sanitize-umm-collection-citations
      sanitize-umm-file-distribution-media))

(defn sanitized-umm-g-record
  "Include only the sanitizers needed for a given umm-g record."
  [record]
  record)

(defn sanitized-umm-s-record
  "Include only the sanitizers needed for a given umm-s record."
  [record]
  (-> record
      (sanitize-umm-record-urls :service)))

(defn sanitized-umm-t-record
  "Place holder for the sanitizers needed for a given umm-t record."
  [record]
  record)

(defn sanitized-umm-sub-record
  "Place holder for the sanitizers needed for a given umm-sub record."
  [record]
  ;; sanitize the CollectionConceptId in the generated umm-sub record
  (let [{sub-type :Type coll-concept-id :CollectionConceptId} record]
    (if (= "collection" sub-type)
      (assoc record :CollectionConceptId nil)
      (if (and (= "granule" sub-type)
               (string/blank? coll-concept-id))
        (assoc record :CollectionConceptId "C1-PROV1")
        record))))

(defn sanitized-umm-var-record
  "Include only the sanitizers needed for a given umm-var record."
  [record]
  (sanitize-science-keywords record))
