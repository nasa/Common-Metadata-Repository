(ns cmr.search.services.result-format-helper
  "Defines helper functions to support result format. Result format is either a keyword or a map in
  the format of {:format umm-json :version \"1.3\"}. Currently, only umm json has version support."
  (:require [cmr.common.mime-types :as mt]
            [cmr.umm-spec.versioning :as ver]))

(defn printable-result-format
  "Returns the given result format in a printable format"
  [result-format]
  (if (map? result-format)
    (let [{:keys [format version]} result-format]
      (if version
        (str format " " version)
        format))
    result-format))

;; TODO consider memoizing this function.
(defn search-result-format->mime-type
  "Returns the mime-type of the given search result format"
  [result-format]
  {:pre [(or (keyword? result-format)
             (and (map? result-format) (keyword? (:format result-format))))]}
  (if (map? result-format)
    (let [{:keys [format version]} result-format
          version (or version ver/current-version)]
      (mt/with-version (mt/format->mime-type format) version))
    (if (string? result-format)
      result-format
      (mt/format->mime-type result-format))))

(defn mime-type->search-result-format
  "Returns the search result format of the given mime type"
  [mime-type]
  (let [result-format (mt/mime-type->format mime-type)]
    (if-let [version (mt/version-of mime-type)]
      {:format result-format :version version}
      result-format)))
