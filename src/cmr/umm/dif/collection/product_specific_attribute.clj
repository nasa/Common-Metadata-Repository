(ns cmr.umm.dif.collection.product-specific-attribute
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [clj-time.format :as f]
            [camel-snake-kebab :as csk]
            [cmr.common.services.errors :as errors]
            [cmr.umm.generator-util :as gu]
            [cmr.umm.echo10.collection.product-specific-attribute :as echo10]
            [cmr.umm.dif.collection.extended-metadata :as em]))

(def ADDITIONAL_ATTRIBUTE_EXTERNAL_META_NAME
  "AdditionalAttribute")

(defn xml-elem->ProductSpecificAttributes
  [xml-struct]
  ;; DIF: Extended_Metadata.Group=AdditionalAttribute
  (when-let [ems (em/xml-elem->extended-metadatas xml-struct true)]
    (let [attribs (filter #(= ADDITIONAL_ATTRIBUTE_EXTERNAL_META_NAME (:group %)) ems)]
      ;; TODO there is no way to validate the AdditionalAttributes through DIF schema.
      ;; For now, we just assume that the type always exist for DIF PSA.
      (seq (map (fn [attr]
                  (let [{:keys [name data-type description value]} attr
                        {:keys [begin end value]} value
                        data-type (echo10/parse-data-type data-type)]
                    (c/map->ProductSpecificAttribute
                      {:name name
                       :data-type data-type
                       :description description
                       :parameter-range-begin (echo10/parse-value data-type begin)
                       :parameter-range-end (echo10/parse-value data-type end)
                       :value (echo10/parse-value data-type value)})))
                attribs)))))

(defn generate-product-specific-attributes
  [psas]
  (when (and psas (not (empty? psas)))
    (em/generate-extended-metadatas psas true)))

