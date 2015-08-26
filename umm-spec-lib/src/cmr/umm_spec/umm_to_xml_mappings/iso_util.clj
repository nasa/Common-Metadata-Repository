(ns cmr.umm-spec.umm-to-xml-mappings.iso-util)

(defn gen-id
  [_]
  (str "d" (java.util.UUID/randomUUID)))
