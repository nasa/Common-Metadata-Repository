(ns cmr.metadata-db.test.util
  "Contains utilities for testing"
  (:require [clojure.string :as str]))


(defn message->regex
  "Converts an expected message into the a regular expression that matches the exact string.
  Handles escaping special regex characters"
  [msg]
  (-> msg
      (str/replace #"\[" "\\\\[")
      (str/replace #"\]" "\\\\]")
      re-pattern))