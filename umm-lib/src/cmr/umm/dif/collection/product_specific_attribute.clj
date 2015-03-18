(ns cmr.umm.dif.collection.product-specific-attribute
  (:require [cmr.umm.collection :as c]
            [cmr.umm.collection.product-specific-attribute :as psa]
            [cmr.umm.dif.collection.extended-metadata :as em]))

(def ADDITIONAL_ATTRIBUTE_EXTERNAL_META_NAME
  "AdditionalAttribute")

(defn xml-elem->ProductSpecificAttributes
  [xml-struct]
  ;; DIF: Extended_Metadata.Group=AdditionalAttribute
  (when-let [ems (em/xml-elem->extended-metadatas xml-struct true)]
    (let [attribs (filter #(= ADDITIONAL_ATTRIBUTE_EXTERNAL_META_NAME (:group %)) ems)]
      ;; There is no way to validate the AdditionalAttributes through DIF schema.
      ;; For now, we just assume that the type always exist for DIF PSA.
      (seq (map (fn [attr]
                  (let [{:keys [name data-type description value]} attr
                        {:keys [begin end value]} value
                        data-type (psa/parse-data-type data-type)]
                    (c/map->ProductSpecificAttribute
                      {:name name
                       :data-type data-type
                       :description description
                       :parameter-range-begin begin
                       :parameter-range-end end
                       :value value
                       :parsed-parameter-range-begin (psa/safe-parse-value data-type begin)
                       :parsed-parameter-range-end (psa/safe-parse-value data-type end)
                       :parsed-value (psa/safe-parse-value data-type value)})))
                attribs)))))

(defn generate-product-specific-attributes
  [psas]
  (when (seq psas)
    (em/generate-extended-metadatas psas true)))

