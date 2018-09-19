(ns cmr.common.test.url-util
  "Contains utilities for testing with URLs."
  (:require [clojure.string :as string]))

(defn url->comparable-url
  "Convert URL to a set consisting of the base-url and the url-parameters for comparison."
  [url]
  (when url
    (let [split-url (string/split url #"\?" 2)
          base-url (first split-url)
          url-params (when-let [params (second split-url)]
                       (string/split params #"\&"))]
      (reduce conj #{base-url} url-params))))
