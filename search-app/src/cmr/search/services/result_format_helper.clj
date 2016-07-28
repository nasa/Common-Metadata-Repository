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

