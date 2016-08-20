(ns cmr.umm.dif.collection.product-specific-attribute
  (:require [cmr.umm.umm-collection :as c]
            [cmr.umm.collection.product-specific-attribute :as psa]
            [cmr.umm.dif.collection.extended-metadata :as em]
            [cmr.common.util :as util]))

(def all-data-types
  "List of all potential data types."
  [:datetime :date :time :int :float :boolean :string])

(defn xml-elem->ProductSpecificAttributes
  [xml-struct]
  (when-let [ems (em/xml-elem->additional-attributes xml-struct)]
    (seq (map (fn [attr]
                (let [{:keys [name data-type description value group]} attr
                      ;; Data-type is optional in DIF9 and in practice not used. We make a best
                      ;; attempt to determine the type of the data. If we cannot determine from
                      ;; the value we default to string.
                      data-type (if data-type
                                  (psa/parse-data-type data-type)
                                  (or (first (filter #(some? (psa/safe-parse-value % value))
                                                 all-data-types))
                                      :string))
                      description (if (empty? description)
                                    c/not-provided
                                    description)]
                  (c/map->ProductSpecificAttribute
                    (util/remove-nil-keys
                      {:name name
                       :group group
                       :data-type data-type
                       :description description
                       :value value
                       :parsed-value (psa/safe-parse-value data-type value)}))))
              ems))))

(defn generate-product-specific-attributes
  [psas]
  (when (seq psas)
    (em/generate-metadata-elements psas)))
