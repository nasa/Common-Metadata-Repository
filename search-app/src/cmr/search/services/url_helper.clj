(ns cmr.search.services.url-helper
  "Defines functions to construct search urls"
  (:require [cmr.common.config :as cfg]))

(defn search-root
  "Returns the url root for search app"
  [context]
  (let [{:keys [protocol host port relative-root-url]} (get-in context [:system :search-public-conf])
        port (if (empty? relative-root-url) port (format "%s%s" port relative-root-url))]
    (format "%s://%s:%s/" protocol host port)))

(defn reference-root
  "Returns the url root for reference location"
  [context]
  (str (search-root context) "concepts/"))

(defn atom-request-url
  "Returns the atom request url based on search concept type with extension and query string"
  [context concept-type result-format]
  (let [{:keys [query-string]} context
        query-string (if (empty? query-string) "" (str "?" query-string))]
    (format "%s%ss.%s%s" (search-root context) (name concept-type) (name result-format) query-string)))

;; Needed to construct opendata fields
(def public-reverb-root (cfg/config-value :public-reverb-root "http://localhost:10000/reverb"))