(ns cmr.umm.generator-util
  "Contains helper functions for generating XML"
  (:require [clojure.data.xml :as x]))

(defn optional-elem
  "Returns the xml element if value is not null"
  [element-name value]
  (when-not (nil? value)
    (x/element element-name {} value)))