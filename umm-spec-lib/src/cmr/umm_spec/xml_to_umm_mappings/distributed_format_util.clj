(ns cmr.umm-spec.xml-to-umm-mappings.distributed-format-util
  "Defines a utility to parse out multiple distribution format values as a
   string into vector components."
  (:require
   [clojure.string :as string]))

(defn- remove-emtpy-strings
  "Remove empty strings from the passed in vector"
  [vector]
  (into []
    (remove #(string/blank? %) vector)))

(defn parse-distribution-formats-replace-comma-and-with-comma
  "Replace ', and' (comma space and) with just ', ' (comma space). The input
  is a string and the output is either the replaced string or the input string
  if no changes occured."
  [formats]
  (if (string/includes? formats ",")
    (string/replace formats #", and " ", ")
    formats))

(defn parse-distribution-formats-replace-comma-or-with-comma
  "Replace ', or' (comma space or) with just ', ' (comma space). The input is a
   string and the output is either the replaced string or the input string if no
   changes occured."
  [formats]
  (if (string/includes? formats ",")
    (string/replace formats #", or " ", ")
    formats))

(defn parse-distribution-formats-split-by-slash-input-string
  "Split the incomming string by '/' except for 'ar/info' and 'arc/info' with
   insensitive case. The result is a vector of split string if '/' exists or
   the input string if '/' doesn't exsit."
  [formats]
  (if (string/includes? formats "/")
    (if-not (or (string/includes? (string/lower-case formats) "ar/info")
                (string/includes? (string/lower-case formats) "arc/info"))
      (string/split formats #"/ *")
      formats)
    formats))

(defn parse-distribution-formats-split-by-slash
  "Split the incomming string or vector of strings by '/' except for 'ar/info'
   and 'arc/info' with insensitive case. The result is a vector of split string
   if '/' exists or the input string if '/' doesn't exsit."
  [formats]
  (if (instance? String formats)
    (parse-distribution-formats-split-by-slash-input-string formats)
    (flatten
      (map parse-distribution-formats-split-by-slash-input-string formats))))

(defn parse-distribution-formats-split-by-and-input-string
  "Split the incomming string by ' and ' except for .r followed by any 2 characters
   followed by ' and ' and .q plus anything after. The result is a vector of split
   string if ' and ' exists or the input string if ' and ' does not exist."
  [formats]
  (if (string/includes? formats " and ")
    (if-not (boolean (re-find #"\.r.?.? and \.q" formats))
      (string/split formats #" * and  *")
      formats)
    formats))

(defn parse-distribution-formats-split-by-and
  "Split the incomming string or vector of strings by ' and ' except for .r followed
   by any 2 characters followed by ' and ' and .q plus anything after. The result is
   a vector of split string if ' and ' exists or the input string if ' and ' does not
   exist."
  [formats]
  (if (instance? String formats)
    (parse-distribution-formats-split-by-and-input-string formats)
    (flatten
      (map parse-distribution-formats-split-by-and-input-string formats))))

(defn parse-distribution-formats-split-by-or-input-string
  "Split the incomming string by ' or ' The result is a vector of split string if ' or '
   exists or the input string if ' or ' does not exist."
  [formats]
  (if (string/includes? formats " or ")
    (string/split formats #" * or  *")
    formats))

(defn parse-distribution-formats-split-by-or
  "Split the incomming string or vector of strings by ' or '."
  [formats]
  (if (instance? String formats)
    (parse-distribution-formats-split-by-or-input-string formats)
    (flatten
      (map parse-distribution-formats-split-by-or-input-string formats))))

(defn parse-distribution-formats-split-by-comma-input-string
  "Split the incomming string by ', ' The result is a vector of split
   string if ', ' exists or the input string if ', ' does not exist."
  [formats]
  (if (string/includes? formats ",")
    (remove-emtpy-strings (string/split formats #", *"))
    formats))

(defn parse-distribution-formats-split-by-comma
  "Split the incomming string or vector of strings by ', '."
  [formats]
  (if (instance? String formats)
    (parse-distribution-formats-split-by-comma-input-string formats)
    (flatten
      (map parse-distribution-formats-split-by-comma-input-string formats))))

(defn parse-distribution-formats-split-by-dash-input-string
  "Split the incomming string by ' - ' The result is a vector of split
   string if ' - ' exists or the input string if ' - ' does not exist."
  [formats]
  (if (string/includes? formats " - ")
    (string/split formats #" - ")
    formats))

(defn parse-distribution-formats-split-by-dash
  "Split the incomming string or vector of strings by ' - '."
  [formats]
  (if (instance? String formats)
    (parse-distribution-formats-split-by-dash-input-string formats)
    (flatten
      (map parse-distribution-formats-split-by-dash-input-string formats))))

(defn parse-distribution-formats-split-by-semicolon-input-string
  "Split the incomming string by ';' except for ;amp and ;gt. The result
   is a vector of split string if ';' exists or the input string if ';'
   does not exist."
  [formats]
  (if (string/includes? formats ";")
    (string/split formats #" *; *(?!amp)(?!gt)")
    formats))

(defn parse-distribution-formats-split-by-semicolon
  "Split the incoming string or vector of strings by ';' except for ;amp and ;gt."
  [formats]
  (if (instance? String formats)
    (parse-distribution-formats-split-by-semicolon-input-string formats)
    (flatten
      (map parse-distribution-formats-split-by-semicolon-input-string formats))))

(defn parse-distribution-formats
  "Split the incoming string by all above types. The replacing of (, and) and
   (, or) by a comma space needs to be done first. Then the rest of the order
   doesn't matter."
  [formats]
  (if-not (nil? formats)
    (let [result (->> formats
                   (parse-distribution-formats-replace-comma-and-with-comma)
                   (parse-distribution-formats-replace-comma-or-with-comma)
                   (parse-distribution-formats-split-by-slash)
                   (parse-distribution-formats-split-by-and)
                   (parse-distribution-formats-split-by-or)
                   (parse-distribution-formats-split-by-comma)
                   (parse-distribution-formats-split-by-dash)
                   (parse-distribution-formats-split-by-semicolon))]
      (if (instance? String result)
        (vector result)
        (remove-emtpy-strings result)))
    []))
