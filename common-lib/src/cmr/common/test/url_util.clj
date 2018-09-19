(ns cmr.common.test.url-util
  "Contains utilities for testing with URLs."
  (:require [clojure.string :as str]))

(defn url->comparable-url
  "Convert URL to a set consisting of the base-url and the url-parameters for comparison."
  [url]
  (when url
    (let [split-url (str/split url #"\?" 2)
          base-url (first split-url)
          url-params (when-let [params (second split-url)]
                       (str/split params #"\&"))]
      (reduce conj #{base-url} url-params))))
