(ns cmr.umm-spec.util
  "This contains utilities for the UMM Spec code."
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [cheshire.factory :as factory]
            [cmr.common.util :as util]
            [cmr.umm-spec.xml.parse :as p]
            [cmr.umm-spec.simple-xpath :refer [select]]))

(def not-provided
  "place holder string value for not provided string field"
  "Not provided")

(def default-granule-spatial-representation
  "Default value for GranuleSpatialRepresentation"
  "CARTESIAN")

(defn load-json-resource
  "Loads a json resource from the classpath. The JSON file may contain comments which are ignored"
  [json-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp json-resource) true)))

(defn convert-empty-record-to-nil
  "Converts empty record to nil."
  [record]
  (if (seq (util/remove-nil-keys record))
    record
    nil))

(defn remove-empty-records
  "Returns the given records with empty records removed from it."
  [records]
  (->> records
       (keep convert-empty-record-to-nil)
       seq))

(defn with-default
  "Returns the value if it exists or returns the default value 'Not provided'."
  [value]
  (if (some? value)
    value
    not-provided))

(defn without-default
  "Returns nil if x is the not-provided placeholder value, else returns x."
  [x]
  (when (not= x not-provided)
    x))

(defn without-default-value-of
  "Returns the parsed value of the given doc on the given xpath and converting the 'Not provided'
  default value to nil."
  [doc xpath]
  (let [value (p/value-of doc xpath)]
    (when-not (= value not-provided)
      value)))

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

(def ^:private data-size-re
  "Regular expression used to parse file sizes from a string. Supports extracting a single value
  with units as well as a range with units."
  #"(-?[0-9][0-9,]*\.?[0-9]*|-?\.[0-9]+) ?((k|kb|ko|kilo|mb|mega|mbyte|mo|gbyte|gb|go|giga|tbyte|tb|tera|p|pb|peta)?(byte)?s?)\b")

(defn parse-data-sizes
  "Parses the data size and units from the provided string. Returns a sequence of maps with the Size
  and the Unit. Returns nil if the string cannot be parsed into file sizes with units."
  [s]
  (seq
    (for [[_ num-str unit-str :as results] (re-seq data-size-re
                                                   (-> s str .toLowerCase))
          :when (and num-str (not (str/blank? unit-str)))]
      {:Size (Double. (.replace num-str "," ""))
       :Unit (-> unit-str str .trim .toUpperCase first (str "B"))})))

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
