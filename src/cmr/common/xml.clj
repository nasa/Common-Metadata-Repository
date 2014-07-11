(ns cmr.common.xml
  "Contains XML helpers for extracting data from XML structs created using clojure.data.xml.
  See the test file for examples."
  (:require [cmr.common.date-time-parser :as p]
            [clojure.string :as str]))

(defn remove-xml-processing-instructions
  "Removes xml processing instructions from XML so it can be embedded in another XML document"
  [xml]
  (let [processing-regex #"<\?.*?\?>"
        doctype-regex #"<!DOCTYPE.*?>"]
    (-> xml
        (str/replace processing-regex "")
        (str/replace doctype-regex ""))))

(defn- children-by-tag
  "Extracts the child elements with the given tag name."
  [element tag]
  (filter #(= tag (:tag %)) (:content element)))

(defn elements-at-path
  "Extracts the children down the specified path."
  [element path]
  (reduce (fn [elements tag]
            (mapcat #(children-by-tag % tag) elements))
          [element]
          path))

(defn update-elements-at-path
  "Calls updater-fn with each element at the specified path. Replaces the element with the result of
  calling the function. This has not been optimized for speed. Works by recursively replacing elements
  that are the parents of the updated nodes. Calls updater-fn with the element and any supplied args."
  [element path updater-fn & args]
  (if (= 0 (count path))
    (apply updater-fn (cons element args))
    (let [tag (first path)
          rest-of-path (rest path)]
      (update-in
        element [:content]
        (fn [children]
          (map (fn [child]
                 (if (= tag (:tag child))
                   (apply update-elements-at-path
                          (concat [child rest-of-path updater-fn] args))
                   child))
               children))))))

(defn element-at-path
  "Returns a single element from within an XML structure at the given path."
  [xml-struct path]
  (first (elements-at-path xml-struct path)))

(defn contents-at-path
  "Pulls the contents from the elements found at the given path."
  [xml-struct path]
  (map :content (elements-at-path xml-struct path)))

(defn content-at-path
  "Extracts the content from the first element at the given path."
  [xml-struct path]
  (first (contents-at-path xml-struct path)))

(defn attrs-at-path
  "This is a helper that will pull the XML attributes from the xml-struct at the given path."
  [xml-struct path]
  (some-> (element-at-path xml-struct path)
          :attrs))

(defn strings-at-path
  "Extracts all the strings from the given path in the XML structure."
  [xml-struct path]
  (map str (apply concat (contents-at-path xml-struct path))))

(defn string-at-path
  "Extracts a string from the given path in the XML structure."
  [xml-struct path]
  (first (strings-at-path xml-struct path)))

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

(defn datetimes-at-path
  "Extracts all the datetimes from the given path in the XML structure."
  [xml-struct path]
  (map p/parse-datetime (strings-at-path xml-struct path)))

(defn datetime-at-path
  "Extracts a datetime from the given path in the XML structure."
  [xml-struct path]
  (first (datetimes-at-path xml-struct path)))

