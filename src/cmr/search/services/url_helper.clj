(ns cmr.search.services.url-helper
  "Defines functions to construct search urls")

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
  [context concept-type-w-extension query-string]
  (let [concept-type-w-extension (if (re-find #"atom" concept-type-w-extension)
                                   concept-type-w-extension
                                   (str concept-type-w-extension ".atom"))
        query-string (if (empty? query-string) query-string (str "?" query-string))]
    (format "%s%s%s" (search-root context) concept-type-w-extension query-string)))