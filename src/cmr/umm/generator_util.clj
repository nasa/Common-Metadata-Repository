(ns cmr.umm.generator-util
  "Contains helper functions for generating XML"
  (:require [clojure.data.xml :as x]))

(defn optional-elem
  "Returns the xml element if value is not null"
  [element-name value]
  (when-not (nil? value)
    (x/element element-name {} value)))

(def alphanumeric "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890")

(defn- get-random-id [length]
  (loop [acc []]
    (if (= (count acc) length) (apply str acc)
      (recur (conj acc (rand-nth alphanumeric))))))

(defn generate-id
  "Returns a 5 character random id to use as an ISO id"
  []
  (str "d" (java.util.UUID/randomUUID)))
