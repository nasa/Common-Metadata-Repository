(ns cmr.umm-spec.opendap-util
  "This contains utilities for OPeNDAP related urls.")

(def opendap-url-content-type-str  "DistributionURL")
(def opendap-url-type-str "USE SERVICE API")
(def opendap-url-subtype-str "OPENDAP DATA")

(defn opendap-url?
  "Determines if the related-url is a OPeNDAP service url."
  [related-url]
  (and (= (:URLContentType related-url) opendap-url-content-type-str)
       (= (:Type related-url) opendap-url-type-str)
       (= (:Subtype related-url) opendap-url-subtype-str)))
