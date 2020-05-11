(ns cmr.common.regex-builder
  "Provides functions for programmatically building a regular expression. All of the regular
  expression functions work by joining strings together. Call compile-regex to create the final
  regular expression"
  (:require
   [clojure.string :as string]))

(defn group
  "Groups together items in a sequence."
  [& parts]
  (if (= (count parts) 1)
      (first parts)
      (str "(?:" (string/join "" parts) ")")))

(defn capture
  "Groups together items in a sequence that will be captured"
  [& parts]
  (if (= (count parts) 1)
      (str "(" (first parts) ")")
      (str "(" (string/join "" parts) ")")))

(defn- build-postfix-op
  "Helper for building a postfix operation that can also group items"
  [op-str]
  (fn [& parts]
    (str (apply group parts ) op-str)))

(def optional
  "Takes one or more items to group that will be made optional"
  (build-postfix-op "?"))

(def one-or-more
  "Takes one or more items as a group that must appear 1 or more times."
  (build-postfix-op "+"))

(def zero-or-more
  "Takes one or more items as a group that may appear 0 or more times."
  (build-postfix-op "*"))

(defn n-times
  "Indicates the group will occur exactly n times."
  [n & parts]
  (str (apply group parts) "{" n "}"))

(defn n-or-more-times
  "Indicates the group will occur at least n times and possibly more."
  [n & parts]
  (str (apply group parts) "{" n ",}"))

(defn n-to-m-times
  "Indicates the group will occur between n and m times."
  [n m & parts]
  (str (apply group parts) "{" n "," m "}"))

(defn choice
  "Creates a choice between the individual "
  [& parts]
  (str "(?:" (string/join "|" parts) ")"))

(defn compile-regex
  [pattern]
  (re-pattern pattern))

(def digit
  "\\d")

(def decimal-number
  (let [sign "[+\\-]"]
    (group (optional sign)
           (one-or-more digit)
           (optional "\\." (one-or-more digit)))))
