(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format")

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:Platform :ProcessingLevel :RelatedUrl :DataDate :ResponsibleOrganization :ScienceKeyword
    :SpatialExtent :TemporalExtent})

(defn- dissoc-not-implemented-fields
  "Removes not implemented fields since they can't be used for comparison"
  [record]
  (reduce (fn [r field]
            (assoc r field nil))
          record
          not-implemented-fields))

(def ^:private formats->expected-conversion-fns
  "A map of metadata formats to expected conversion functions"
  {:echo10 dissoc-not-implemented-fields
   :iso19115 dissoc-not-implemented-fields
   :iso-smap dissoc-not-implemented-fields
   :dif dissoc-not-implemented-fields
   :dif10 dissoc-not-implemented-fields})

(defn metadata-format->expected-conversion
  "Takes a metadata format and returns the function that can convert the UMM record used as input
  into the expected parsed UMM."
  [metadata-format]
  ;; identity is used if no conversion is needed.
  (get formats->expected-conversion-fns metadata-format identity))
