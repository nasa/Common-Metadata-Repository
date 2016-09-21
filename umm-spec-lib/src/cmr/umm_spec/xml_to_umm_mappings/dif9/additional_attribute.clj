(ns cmr.umm-spec.xml-to-umm-mappings.dif9.additional-attribute
  "Defines mappings from DIF 9 Additional Attribute elements into UMM records"
  (:require [cmr.umm-spec.additional-attribute :as aa]
            [cmr.umm-spec.xml-to-umm-mappings.dif9.extended-metadata :as em]
            [cmr.common.util :as util]
            [cmr.umm-spec.util :as su]))

(def all-data-types
  "List of all potential data types."
  ["DATETIME" "DATE" "TIME" "INT" "FLOAT" "BOOLEAN" "STRING"])

(defn- normalize-data-type
  "Returns the given additional attribute with DataType normalized to a reasonable value.
  Data-type is optional in DIF9 and in practice not used. We make a best attempt to determine
  the type of the data. If we cannot determine from the value we default to string."
  [attr]
  (let [{data-type :DataType value :Value} attr
        data-type (if data-type
                    (aa/parse-data-type data-type)
                    (or (first (filter #(some? (aa/safe-parse-value % value))
                                       all-data-types))
                        "STRING"))]
    (-> attr
        (assoc :DataType data-type)
        util/remove-nil-keys)))

(defn- update-additional-atttribute
  "Update the additional attribute to be valid UMM"
  [attr sanitize?]
  (-> attr
      (normalize-data-type)
      (update :Description #(su/with-default % sanitize?))))

(defn xml-elem->AdditionalAttributes
  [doc sanitize?]
  (when-let [aas (em/xml-elem->additional-attributes doc)]
    (seq (map #(update-additional-atttribute % sanitize?) aas))))
