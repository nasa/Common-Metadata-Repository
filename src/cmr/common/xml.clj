(ns cmr.common.xml
  "Contains XML helpers for extracting data from XML structs created using clojure.data.xml.
  See the test file for examples."
  (:require [cmr.common.date-time-parser :as p]))

(declare content-at-path)

(defn element-at-path
  "This is a helper that will pull an XML element from the xml-struct at the given path."
  [xml-struct path]
  (if (sequential? path)
    (let [path (vec path)
          path-to-last (subvec path 0 (dec (count path)))
          container (reduce content-at-path xml-struct path-to-last)]
      (when container (element-at-path container (last path))))
    (cond
      (sequential? xml-struct)
      (->> xml-struct (filter #(= path (:tag %))) first)

      (map? xml-struct)
      (when (= path (:tag xml-struct)) xml-struct)

      :else
      (throw (Exception.
               (format
                 "Unexpected xml-struct at path. path: [%s] xml-struct: [%s]"
                 path xml-struct))))))

(defn elements-at-path
  "This is a helper that will pull an XML element from the xml-struct at the given path."
  [xml-struct path]
  (if (sequential? path)
    (let [path (vec path)
          path-to-last (subvec path 0 (dec (count path)))
          container (reduce content-at-path xml-struct path-to-last)]
      (when container (elements-at-path container (last path))))
    (cond
      (sequential? xml-struct)
      (let [result (filter #(= path (:tag %)) xml-struct)]
        (if (empty? result)
          nil
          result))

      (map? xml-struct)
      (when (= path (:tag xml-struct)) xml-struct)

      :else
      (throw (Exception.
               (format
                 "Unexpected xml-struct at path. path: [%s] xml-struct: [%s]"
                 path xml-struct))))))

(defn content-at-path
  "This is a helper that will pull the XML content from the xml-struct at the given path."
  [xml-struct path]
  (when-let [element (element-at-path xml-struct path)]
    (:content element)))

(defn contents-at-path
  "This is a helper that will pull the XML content from the xml-struct at the given path."
  [xml-struct path]
  (if-let [elements (elements-at-path xml-struct path)]
    (map #(:content %) elements)))

(defn attrs-at-path
  "This is a helper that will pull the XML attributes from the xml-struct at the given path."
  [xml-struct path]
  (when-let [element (element-at-path xml-struct path)]
    (:attrs element)))

(defn string-at-path
  "Extracts a string from the given path in the XML structure."
  [xml-struct path]
  (when-let [content (content-at-path xml-struct path)]
    (str (first content))))

(defn strings-at-path
  "Extracts a string from the given path in the XML structure."
  [xml-struct path]
  (if-let [contents (contents-at-path xml-struct path)]
    (map #(str (first %)) contents)))

(defn long-at-path
  "Extracts a long number from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    (Long. s)))

(defn double-at-path
  "Extracts a double number from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    (Double. s)))

(defn bool-at-path
  "Extracts a boolean from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    (Boolean. s)))

(defn datetime-at-path
  "Extracts a datetime from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    (p/string->datetime s)))

(defn datetimes-at-path
  "Extracts a datetime from the given path in the XML structure."
  [xml-struct path]
  (if-let [value (strings-at-path xml-struct path)]
    (map #(p/string->datetime %) value)))
