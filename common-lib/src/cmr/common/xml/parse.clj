(ns cmr.common.xml.parse
  (:require
   [clojure.string :as str]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.xml.simple-xpath :refer [parse-xpath select text]]))

(defn- blank-to-nil
  "Returns the given string or nil if it is blank."
  [s]
  (when-not (str/blank? s)
    s))

(defn value-of*
  "Returns all elements matching the XPath expression."
  [element xpath]
  (let [value (text (select element xpath))]
    (blank-to-nil value)))

(defmacro value-of
  "Returns all elements matching the XPath expression. Perform work of parsing XPath at compile time
  if a string literal is passed in to improve performance at runtime."
  [element xpath]
  (if (string? xpath)
    (let [parsed (parse-xpath xpath)]
      `(value-of* ~element ~parsed))
    `(value-of* ~element ~xpath)))

(defn values-at*
  "Returns seq of contents of elements at xpath."
  [element xpath]
  (let [values (map text (select element xpath))]
    (map blank-to-nil values)))

(defmacro values-at
  "Perform work of parsing XPath at compile time if a string literal is passed in to improve
  performance at runtime."
  [element xpath]
  (if (string? xpath)
    (let [parsed (parse-xpath xpath)]
      `(values-at* ~element ~parsed))
    `(values-at* ~element ~xpath)))

(defn boolean-at*
  [element xpath]
  (= "true" (value-of element xpath)))

(defmacro boolean-at
  "Perform work of parsing XPath at compile time if a string literal is passed in to improve
  performance at runtime."
  [element xpath]
  (if (string? xpath)
    (let [parsed (parse-xpath xpath)]
      `(boolean-at* ~element ~parsed))
    `(boolean-at* ~element ~xpath)))

(defn fields-from
  [element & kws]
  (zipmap kws
          (for [kw kws]
            (value-of element (name kw)))))

(defn dates-at*
  [element xpath]
  (map #(dtp/parse-datetime (text %)) (select element xpath)))

(defmacro dates-at
  "Perform work of parsing XPath at compile time if a string literal is passed in to improve
  performance at runtime."
  [element xpath]
  (if (string? xpath)
    (let [parsed (parse-xpath xpath)]
      `(dates-at* ~element ~parsed))
    `(dates-at* ~element ~xpath)))

(defn date-at*
  [element xpath]
  (when-let [value (text (value-of element xpath))]
    (dtp/parse-datetime value)))

(defmacro date-at
  "Perform work of parsing XPath at compile time if a string literal is passed in to improve
  performance at runtime."
  [element xpath]
  (if (string? xpath)
    (let [parsed (parse-xpath xpath)]
      `(date-at* ~element ~parsed))
    `(date-at* ~element ~xpath)))

(defn date-at-str
  "Return element date at xpath as string if not empty else nil."
  [element xpath]
  (not-empty (str (date-at element xpath))))

(defn dates-at-str
  "Return element dates at xpath as string values if date is not nil else nil."
  [element xpath]
  (map #(not-empty (str %)) (dates-at element xpath)))
