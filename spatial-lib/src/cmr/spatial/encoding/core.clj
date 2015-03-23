(ns cmr.spatial.encoding.core)

(defmulti encode
  "Returns a CMR spatial object in the requested format."
  (fn [format obj]
    [format (type obj)]))

(defmulti decode
  "Returns a CMR spatial object by parsing the value in the specified format."
  (fn [format x]
    [format (:tag x (type x))]))
