(ns cmr.search.services.url-helper
  "Defines functions to construct search urls"
  (:require [cmr.common.config :as cfg]
            [cmr.transmit.config :as tconfig]))

(defn reference-root
  "Returns the url root for reference location"
  [context]
  (str (tconfig/application-public-root-url context) "concepts/"))

(defn atom-request-url
  "Returns the atom request url based on search concept type with extension and query string"
  [context concept-type result-format]
  (let [{:keys [query-string]} context
        query-string (if (empty? query-string) "" (str "?" query-string))]
    (format "%s%ss.%s%s" (tconfig/application-public-root-url context)
            (name concept-type) (name result-format) query-string)))