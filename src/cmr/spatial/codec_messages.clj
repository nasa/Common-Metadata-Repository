(ns cmr.spatial.codec-messages
  "Contains error messages for code decoding failures"
  (:require [camel-snake-kebab :as csk]))

(defn shape-decode-msg
  [type s]
  (format "[%s] is not a valid URL encoded %s" s (csk/->snake_case_string type)))