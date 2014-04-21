(ns cmr.umm.echo10.collection.product-specific-attribute
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [clj-time.format :as f]
            [camel-snake-kebab :as csk]
            [cmr.common.services.errors :as errors]
            [cmr.umm.generator-util :as gu]))

(defn parse-data-type
  "Parses the string data type from the XML into the keyword data type."
  [data-type]
  (keyword (csk/->kebab-case data-type)))

(defn gen-data-type
  "Generates the string data type for XML from the keyword data type."
  [data-type]
  (csk/->SNAKE_CASE (name data-type)))

(defmulti parse-value
  "Parses a value based on the data type given"
  (fn [data-type value]
    data-type))

(defmethod parse-value :default
  [data-type value]
  (when value
    (str value)))

(defmethod parse-value :int
  [data-type ^String value]
  (when value (Long. value)))

(defmethod parse-value :float
  [data-type ^String value]
  (when value (Double. value)))

(defmethod parse-value :boolean
  [data-type ^String value]
  (when value
    (case value
      "true" true
      "false" false
      "1" true
      "0" false
      :else (errors/internal-error! (format "Unexpected boolean value [%s]" value)))))

(def datetime-regex->formatter
  "A map of regular expressions matching a date time to the formatter to use"
  {#"^[^T]+T[^.]+\.\d+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :date-time)
   #"^[^T]+T[^.]+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :date-time-no-ms)
   #"^[^T]+T[^.]+\.\d+$" (f/formatters :date-hour-minute-second-ms)
   #"^[^T]+T[^.]+$" (f/formatters :date-hour-minute-second)})

(def time-regex->formatter
  "A map of regular expressions matching a time to the formatter to use"
  {#"^[^.]+\.\d+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :time)
   #"^[^.]+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :time-no-ms)
   #"^[^.]+\.\d+$" (f/formatters :hour-minute-second-ms)
   #"^[^.]+$" (f/formatters :hour-minute-second)})

(defn find-formatter
  [datetime regex-formatter-map]
  (->> regex-formatter-map
       (filter (fn [[regex formatter]]
                 (re-matches regex datetime)))
       first
       second))

(defmethod parse-value :datetime
  [data-type value]
  (when value
    (f/parse (find-formatter value datetime-regex->formatter) value)))

(defmethod parse-value :time
  [data-type value]
  (when value
    (f/parse (find-formatter value time-regex->formatter) value)))

(defmethod parse-value :date
  [data-type value]
  (when value
    (f/parse (f/formatters :date) value)))


(defn xml-elem->ProductSpecificAttribute
  [psa-elem]
  (let [name (cx/string-at-path psa-elem [:Name])
        description (cx/string-at-path psa-elem [:Description])
        data-type (parse-data-type (cx/string-at-path psa-elem [:DataType]))
        begin (parse-value data-type (cx/string-at-path psa-elem [:ParameterRangeBegin]))
        end (parse-value data-type (cx/string-at-path psa-elem [:ParameterRangeEnd]))
        value (parse-value data-type (cx/string-at-path psa-elem [:Value]))]
    (c/map->ProductSpecificAttribute
      {:name name :description description :data-type data-type
       :parameter-range-begin begin :parameter-range-end end
       :value value})))

(defn xml-elem->ProductSpecificAttributes
  [collection-element]
  (let [psas (map xml-elem->ProductSpecificAttribute
                  (cx/elements-at-path
                    collection-element
                    [:AdditionalAttributes :AdditionalAttribute]))]
    (when (not (empty? psas))
      psas)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defmulti gen-value
  "Converts the given value to a string for placement in XML."
  (fn [data-type value]
    data-type))

(defmethod gen-value :default
  [data-type value]
  (when (not (nil? value))
    (str value)))

(defmethod gen-value :time
  [data-type value]
  (when value
    (f/unparse (f/formatters :hour-minute-second-ms) value)))

(defmethod gen-value :date
  [data-type value]
  (when value
    (f/unparse (f/formatters :date) value)))

(defn generate-product-specific-attributes
  [psas]
  (when (and psas (not (empty? psas)))
    (x/element
      :AdditionalAttributes {}
      (for [psa psas]
        (let [{:keys [data-type name description parameter-range-begin parameter-range-end value]} psa]
          (x/element :AdditionalAttribute {}
                     (x/element :Name {} name)
                     (x/element :DataType {} (gen-data-type data-type))
                     (x/element :Description {} description)
                     (gu/optional-elem :ParameterRangeBegin (gen-value data-type parameter-range-begin))
                     (gu/optional-elem :ParameterRangeEnd (gen-value data-type parameter-range-end))
                     (gu/optional-elem :Value (gen-value data-type value))))))))

