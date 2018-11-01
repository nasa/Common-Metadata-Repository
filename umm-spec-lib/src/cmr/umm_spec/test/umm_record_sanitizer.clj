(ns cmr.umm-spec.test.umm-record-sanitizer
  "This contains functions for manipulating the generator generated umm-record to a sanitized
  version to pass the xml validation for various supported metadata format. This is needed because
  the incompatibility between UMM JSON schema and schemas of the various metadata formats making the
  generated metadata xml invalid without some kind of sanitization."
  (:require
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as test]
   [cmr.common.util :as util :refer [update-in-each]]
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

(defn valid-types-for-url-content-type
  "Returns all valid Types for URLContentType"
  [url-content-type concept]
  (case concept
    :collection (keys (get spec-util/valid-url-content-types-map url-content-type))
    :service (keys (get valid-url-content-types-map-service-version-1-3 url-content-type))
    (assert nil (str "The RelatedUrl URLContentType, Type, and Subtype valid values are not
                      defined for the " concept " concept."))))

(defn valid-subtypes-for-type
  "Returns all Subtypes for URLContentType/Type combination"
  [url-content-type type concept]
  (case concept
    :collection (get-in spec-util/valid-url-content-types-map [url-content-type type])
    :service (get-in valid-url-content-types-map-service-version-1-3 [url-content-type type])
    (assert nil (str "The RelatedUrl URLContentType, Type, and Subtype valid values are not
                      defined for the " concept " concept."))))

(defn- generate-valid-type-and-subtype-for-url-content-type
 "Generate a valid URLContentType, Type, and Subtype combo given the URLContentType"
 [url-content-type concept]
 (let [valid-types (gen/elements (valid-types-for-url-content-type url-content-type concept))
       types (gen/sample valid-types 1)
       valid-subtypes (valid-subtypes-for-type url-content-type type concept)
       subtypes (when valid-subtypes (gen/sample (gen/elements valid-subtypes) 1))]
  {:Type (first types)
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

(defn- sanitize-related-url
  "Returns a single sanitized RelatedUrl"
  [entry concept]
  (if-let [urls (get-in entry [:RelatedUrl :URL])]
    (let [url-content-type (get-in entry [:RelatedUrl :URLContentType])
          {:keys [Type Subtype]} (generate-valid-type-and-subtype-for-url-content-type url-content-type concept)]
      (-> entry
          (assoc-in [:RelatedUrl :URL] (gen/sample test/file-url-string 1))
          (assoc-in [:RelatedUrl :Type] Type)
          (assoc-in [:RelatedUrl :Subtype] Subtype)
          ;; These two fields cannot ever exist in a CollectionCitations or
          ;; PublicationReferences RelatedUrl
          (update :RelatedUrl dissoc :GetData)
          (update :RelatedUrl dissoc :GetService)))
    entry))

(defn- sanitize-related-urls
  "Returns a list of sanitized related urls"
  [related-urls concept]
  (when related-urls
   (for [ru related-urls
         :let [type-subtype
               (generate-valid-type-and-subtype-for-url-content-type (:URLContentType ru) concept)]]
    (merge
     (-> ru
         sanitize-get-service-and-get-data
         (assoc :URL (first (gen/sample test/file-url-string 1))))
     type-subtype))))

(defn- sanitize-contact-informations
  "Sanitize a record with ContactInformation"
  [records concept]
  (seq (for [record records]
         (if-let [contact-info (:ContactInformation record)]
           (update-in record
                      [:ContactInformation :RelatedUrls]
                      #(sanitize-related-urls % concept))
           record))))

(defn- sanitize-data-centers
  "Sanitize data center contact information and the contact information on contact
  persons and groups associated with the data center"
  [data-centers concept]
  (seq (for [dc (sanitize-contact-informations data-centers concept)]
         (-> dc
             (update :ContactPersons #(sanitize-contact-informations % concept))
             (update :ContactGroups #(sanitize-contact-informations % concept))))))

(defn- sanitize-umm-record-related-urls
  "Sanitize the RelatedUrls on the collection if any exist"
  [record concept]
  (if (seq (:RelatedUrls record))
    (update record :RelatedUrls #(sanitize-related-urls % concept))
    record))

(defn- sanitize-umm-record-data-centers
  "Sanitize data centers if there are any data centers in the record"
  [record concept]
  (if (seq (:DataCenters record))
    (update record :DataCenters #(sanitize-data-centers % concept))
    record))

(defn- sanitize-umm-record-contacts
  "Sanitize RelatedUrls if they key exists."
  [record key concept]
  (if (seq (get record key))
    (update record key #(sanitize-contact-informations % concept))
    record))

(defn- sanitize-umm-record-related-url
  "Sanitize a single RelatedUrl in a collection if they key exists."
  [collection key concept]
  (if (seq (get collection key))
    (update-in-each collection [key] #(sanitize-related-url % concept))
    collection))

(defn- sanitize-online-resource
  "Sanitize the OnlineResource Linkage (url)"
  [c]
  (if (get-in c [:OnlineResource :Linkage])
   (assoc-in c [:OnlineResource :Linkage] (first (gen/sample test/file-url-string 1)))
   c))

(defn- sanitize-umm-record-urls
  "Sanitize all RelatedUrls throughout the collection by making them url strings."
  [record concept]
  (-> record
      (sanitize-umm-record-related-urls concept)
      (sanitize-umm-record-data-centers concept)
      (sanitize-umm-record-contacts :ContactPersons concept)
      (sanitize-umm-record-contacts :ContactGroups concept)
      (sanitize-umm-record-related-url :CollectionCitations concept)
      (sanitize-umm-record-related-url :PublicationReferences concept)))

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
  (if (and (get-in record [:UseConstraints :LicenseUrl])
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
      sanitize-umm-collection-citations))

(defn sanitized-umm-s-record
  "Include only the sanitizers needed for a given umm-s record."
  [record]
  (-> record
      (sanitize-umm-record-urls :service)))

(defn sanitized-umm-var-record
  "Include only the sanitizers needed for a given umm-var record."
  [record]
  (sanitize-science-keywords record))
