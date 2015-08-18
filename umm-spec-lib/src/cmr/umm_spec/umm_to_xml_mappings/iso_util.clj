(ns cmr.umm-spec.umm-to-xml-mappings.iso-util)

(defn gen-id
  []
  (str "d" (java.util.UUID/randomUUID)))
