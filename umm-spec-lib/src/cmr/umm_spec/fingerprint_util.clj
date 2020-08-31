(ns cmr.umm-spec.fingerprint-util
  "This contains utilities for generate fingerprint of variable."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [digest :as digest]))

(defn- normalized-string
  "Returns the given string with leading and trailing whitespaces trimmed
  and converted to lowercase."
  [value]
  (some-> value
          string/trim
          string/lower-case))

(defn- dimension->str
  "Returns the string representation of the given Dimension object for fingerprint calculation."
  [dimension]
  (let [{:keys [Name Size Type]} dimension]
    (format "{:Name %s, :Size %s, :Type %s}"
            (normalized-string Name) Size Type)))

(defn get-variable-fingerprint
  "Returns the fingerprint of the given variable metadata."
  [variable-metadata]
  (let [parsed (json/decode variable-metadata true)
        {:keys [Name Units Dimensions]} parsed
        id-string (format "%s|%s|%s"
                          (normalized-string Name)
                          (normalized-string Units)
                          (pr-str (mapv dimension->str Dimensions)))]
    (digest/md5 id-string)))
