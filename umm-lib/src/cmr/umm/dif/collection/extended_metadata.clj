(ns cmr.umm.dif.collection.extended-metadata
  "Provide functions to parse and generate DIF Extended_Meatadata elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(defn- string-value-with-attr
  "Returns the string value of the first element with the given attribute"
  [elements attr]
  (when-let [elem (first (filter #(= (name attr) (get-in % [:attrs :type])) elements))]
    (str (first (:content elem)))))

(defn- xml-elem->simple-extended-metadata
  [extended-elem]
  (let [name (cx/string-at-path extended-elem [:Name])
        value (cx/string-at-path extended-elem [:Value])]
    {:name name
     :value value}))

(defn- xml-elem->psa-extended-metadata
  "Returns the METADATA fields that we are interested in string format in a map."
  [extended-elem]
  (let [group (cx/string-at-path extended-elem [:Group])
        name (cx/string-at-path extended-elem [:Name])
        description (cx/string-at-path extended-elem [:Description])
        data-type (cx/string-at-path extended-elem [:Type])
        values (cx/elements-at-path extended-elem [:Value])
        begin (string-value-with-attr values :ParamRangeBegin)
        end (string-value-with-attr values :ParamRangeEnd)
        value (string-value-with-attr values :Value)]
    {:group group
     :name name
     :description description
     :data-type data-type
     :value {:begin begin :end end :value value}}))

(defn- xml-elem->extended-metadata
  "Returns the METADATA fields that we are interested in string format in a map."
  [extended-elem is-psa]
  (if is-psa
    (xml-elem->psa-extended-metadata extended-elem)
    (xml-elem->simple-extended-metadata extended-elem)))

(defn xml-elem->extended-metadatas
  "Returns the parsed extended-metadatas, is-psa indicates if the Extended_Metadata is for AdditionalAttribute."
  [collection-element is-psa]
  (let [extended-metadatas (map #(xml-elem->extended-metadata % is-psa)
                                (cx/elements-at-path
                                  collection-element
                                  [:Extended_Metadata :Metadata]))]
    (when-not (empty? extended-metadatas)
      extended-metadatas)))

(defn extended-metadatas-value
  "Returns the single value of the extended metadatas with the given name.
  This is used to extract the value of simple extended metadatas by name."
  [xml-struct extended-metadata-name]
  (when-let [ems (xml-elem->extended-metadatas xml-struct false)]
    (let [elem (filter #(= extended-metadata-name (:name %)) ems)]
      (when (seq elem)
        (:value (first elem))))))

(defn- generate-simple
  [extended-metadatas]
  (for [em extended-metadatas]
    (let [{:keys [name value]} em]
      (x/element :Metadata {}
                 (x/element :Name {} name)
                 (x/element :Value {} value)))))

(defn- generate-psa
  [extended-metadatas]
  (for [em extended-metadatas]
    (let [{:keys [data-type name description parameter-range-begin parameter-range-end value]} em]
      (x/element :Metadata {}
                 (x/element :Group {} "AdditionalAttribute")
                 (x/element :Name {} name)
                 (when description (x/element :Description {} description))
                 (x/element :Type {} (psa/gen-data-type data-type))
                 (when-not (nil? parameter-range-begin)
                   (x/element :Value {:type "ParamRangeBegin"} parameter-range-begin))
                 (when-not (nil? parameter-range-end)
                   (x/element :Value {:type "ParamRangeEnd"} parameter-range-end))
                 (when-not (nil? value)
                   (x/element :Value {:type "Value"} value))))))

(defn generate-extended-metadatas
  "Generate the Extended_Metadatas, is-psa indicates if the Extended_Metadata is for AdditionalAttribute."
  [extended-metadatas is-psa]
  (when (and extended-metadatas (not (empty? extended-metadatas)))
    (x/element :Extended_Metadata {}
               (if is-psa
                 (generate-psa extended-metadatas)
                 (generate-simple extended-metadatas)))))
