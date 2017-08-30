(ns cmr.umm-spec.util
  "This contains utilities for the UMM Spec code."
  (:require
   [clj-time.format :as f]
   [clojure.string :as str]
   [cmr.common.xml.parse :as p]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.common.util :as util]
   [cmr.umm-spec.date-util :as du]
   [cmr.umm-spec.models.umm-common-models :as cmn]))

(def ABSTRACT_MAX
  40000)

(def PURPOSE_MAX
  10000)

(def PROJECT_LONGNAME_MAX
  300)

(def USECONSTRAINTS_MAX
  20000)

(def QUALITY_MAX
  12000)

(def ACCESSCONSTRAINTS_DESCRIPTION_MAX
  4000)

(def SHORTNAME_MAX
  85)

(def valid-url-content-types-map
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
  [url-content-type]
  (keys (get valid-url-content-types-map url-content-type)))

(defn valid-subtypes-for-type
  "Returns all SubTypes for URLContentType/Type combination"
  [url-content-type type]
  (get-in valid-url-content-types-map [url-content-type type]))

(defn type->url-content-type
  "Get the URLContentType from the type"
  [type]
  (first
    (for [url-content-type (keys valid-url-content-types-map)
          :when (some #(= type %) (valid-types-for-url-content-type url-content-type))]
      url-content-type)))

(def default-url-type
 {:URLContentType "PublicationURL"
  :Type "VIEW RELATED INFORMATION"
  :Subtype "GENERAL DOCUMENTATION"})

(def ^:private umm-contact-mechanism-correction-map
  {"phone" "Telephone"
   "Phone" "Telephone"
   "fax" "Fax"})

(def default-parsing-options
  "Defines the default options for parsing metadata into umm"
  {:sanitize? true})

(def skip-sanitize-parsing-options
  "Defines skipping sanitize options for parsing metadata into umm"
  {:sanitize? false})

(def not-provided
  "place holder string value for not provided string field"
  "Not provided")

(def NOT-PROVIDED
  "place holder string value for NOT PROVIDED string field"
  "NOT PROVIDED")

(def NOT-APPLICABLE
  "place holder string value for NOT APPLICABLE string field"
  "NOT APPLICABLE")

(def STRING
  "place holder string value for string field not in the enum list."
  "STRING")

(def not-provided-data-center
  "Place holder to use when a data center is not provided."
  (cmn/map->DataCenterType
    {:Roles ["ARCHIVER"]
     :ShortName not-provided}))

(def not-provided-url
  "Not%20provided")

(def not-provided-related-url
  "Place holder to use when a related url is not provided."
  (cmn/map->RelatedUrlType
   (merge default-url-type
    {:URL not-provided-url})))

(def default-granule-spatial-representation
  "Default value for GranuleSpatialRepresentation"
  "NO_SPATIAL")

(def not-provided-temporal-extents
  "Default temporal extents to use if none is provided"
  [{:RangeDateTimes [{:BeginningDateTime du/parsed-default-date}]}])

(def not-provided-platforms
  "Default platforms to use if none is provided"
  [(cmn/map->PlatformType {:ShortName not-provided})])

(def not-provided-contact-person-role
  "Default role for a Contact Person to use if none is provided"
  "Technical Contact")

(def not-provided-science-keywords
  "Default science keywords to use if none is provided. Use 'EARTH SCIENCE' as the
  category so ISO-SMAP picks it up as a science keyword"
  [(cmn/map->ScienceKeywordType {:Category "EARTH SCIENCE"
                                 :Term not-provided
                                 :Topic not-provided})])

(def not-provided-spatial-extent
  "Default spatial extent to use if none is provided"
  {:GranuleSpatialRepresentation "NO_SPATIAL"})

(defn convert-empty-record-to-nil
  "Converts empty record to nil."
  [record]
  (if (seq (util/remove-nil-keys record))
    record
    nil))

(defn with-default-url
 [url sanitize?]
 (if sanitize?
  (or url not-provided-url)
  url))

(defn remove-empty-records
  "Returns the given records with empty records removed from it."
  [records]
  (->> records
       (keep convert-empty-record-to-nil)
       seq))

(defn country-with-default
  "The default of 'Not provided' is too long so specify an alternative default for country."
  [country]
  (or country "Unknown"))

(defn map-with-default
  "Returns the result of applying the given map function to a list of values. Use the default value
  when the mapped value is nil and sanitize? is true.

  map-function - function to use for mapping
  values - the values to map
  value-default - the default to use if value is not present in the map
  sanitize? - true if the default value should be used"
  [map-function values value-default sanitize?]
  (let [results (map map-function values)]
   (if sanitize?
     (map #(if (some? %) % value-default) results)
     results)))

(defn with-default
  "Returns the value if it exists or returns the default value 'Not provided'."
  ([value]
   (with-default value (:sanitize? default-parsing-options)))
  ([value sanitize?]
   (if sanitize?
     (if (some? value)
       value
       not-provided)
     value)))

(defn default-value?
  "Returns true if value is the umm-spec-lib default/placeholder"
  [value]
  (= value not-provided))

(defn without-default
  "DEPRECATED: We will no longer remove default values.
  Returns nil if x is the not-provided placeholder value, else returns x."
  [x]
  (when-not (= x not-provided)
    x))

(defn without-default-value-of
  "DEPRECATED: We will no longer remove default values.
  Returns the parsed value of the given doc on the given xpath and converting the 'Not provided'
  default value to nil."
  [doc xpath]
  (let [value (p/value-of doc xpath)]
    (without-default value)))

(defn nil-to-empty-string
  "Returns the string itself or empty string if it is nil."
  [s]
  (if (some? s) s ""))

(defn capitalize-words
  "Capitalize every word in a string"
  [s]
  (when s
    (->> (str/split (str s) #"\b")
         (map str/capitalize)
         (str/join))))

(defn generate-id
  "Returns a 5 character random id to use as an ISO id"
  []
  (str "d" (java.util.UUID/randomUUID)))

(defn parse-short-name-long-name
  "Returns the list of ShortName and LongName from parsing the given doc on the given path."
  [doc path]
  (seq (for [elem (select doc path)]
         {:ShortName (p/value-of elem "Short_Name")
          :LongName (p/value-of elem "Long_Name")})))

(defn entry-id
  "Returns the entry-id for the given short-name and version-id."
  [short-name version-id]
  (if (or (nil? version-id)
          (= not-provided version-id))
    short-name
    (str short-name "_" version-id)))

(def ^:private data-size-re
  "Regular expression used to parse file sizes from a string. Supports extracting a single value
  with units as well as a range with units."
  #"(-?[0-9][0-9,]*\.?[0-9]*|-?\.[0-9]+) ?((k|kb|ko|kilo|mb|mega|mbyte|mo|gbyte|gb|go|giga|tbyte|tb|tera|p|pb|peta)?(byte)?s?)\b")

(defn parse-data-sizes
  "Parses the data size and units from the provided string. Returns a sequence of maps with the Size
  and the Unit. Returns nil if the string cannot be parsed into file sizes with units.
  If data sanitization is enabled convert bytes to KB since bytes is not a UMM valid unit."
  [s]
  (seq
    (for [[_ num-str unit-str :as results] (re-seq data-size-re
                                                   (-> s str .toLowerCase))
          :when (and num-str (not (str/blank? unit-str)))]
      (if (= (str/lower-case unit-str) "bytes")
        {:Size (/ (Double. (str/replace num-str "," "")) 1000.0)
         :Unit "KB"}
        {:Size (Double. (str/replace num-str "," ""))
         :Unit (-> unit-str str/trim str/upper-case first (str "B"))}))))

(defn data-size-str
  "Takes a collection of FileSizeType records which have a Size and a Unit and converts them to a
  string representation."
  [sizes]
  (when (seq sizes)
    (str/join ", "
              (for [size sizes]
                (str (:Size size) " " (or (:Unit size) "MB"))))))

(defn closed-counter-clockwise->open-clockwise
  "Returns a sequence of points in open and clockwise order.
  This is the order used by ECHO10 and DIF10."
  [points]
  (reverse (butlast points)))

(defn open-clockwise->closed-counter-clockwise
  "ECHO10 and DIF10 polygon points are \"open\" and specified in clockwise order.
  Returns the sequence of points reversed (counterclockwise) and with the first point
  appended to the end (closed)."
  [points]
  (let [ccw (vec (reverse points))]
    (conj ccw (first ccw))))

(defn coordinate-system
  "Returns the CoordinateSystem of the given UMM geometry. Returns the default CoordinateSystem if
  UMM geometry has any spatial area and CoordinateSystem is not present."
  [geom]
  (let [{:keys [CoordinateSystem GPolygons BoundingRectangles Lines Points]} geom]
    (or CoordinateSystem
        ;; Use default value if CoordinateSystem is not set, but the geometry has any spatial area
        (when (or GPolygons BoundingRectangles Lines Points)
          default-granule-spatial-representation))))

(defn char-string
  "Returns an ISO gco:CharacterString element with the given contents."
  [content]
  [:gco:CharacterString content])

(defn char-string-at
  "Returns an ISO gco:CharacterString with contents taken from the given xpath."
  [context xpath]
  (char-string (select context xpath)))

(defn correct-contact-mechanism
  "Correct the contact mechanism if a correction exists, otherwise return the original"
  [contact-mechanism]
  (get umm-contact-mechanism-correction-map contact-mechanism contact-mechanism))

(defn format-isbn
  "Format the ISBN to make it compliant with UMM"
  [isbn]
  (when (some? isbn)
    (let [isbn (-> isbn
                   str/trim
                   (str/replace "-" "")
                   (str/replace "ISBN" "")
                   (str/replace "ISSN" ""))]
      (when (not (str/blank? isbn))
        isbn))))

(defn truncate
  "Truncate the string if the sanitize option is enabled, otherwise return the original string"
  [s n sanitize?]
  (if sanitize?
    (util/trunc s n)
    s))

(defn truncate-with-default
  "If s is nil and sanitize is true, return 'Not provided'. If sanitize is true, truncate string.
  If sanitize is false, return s with no default or truncation."
  [s n sanitize?]
  (if sanitize?
    (util/trunc (with-default s sanitize?) n)
    s))
