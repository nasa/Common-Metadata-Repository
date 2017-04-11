(ns cmr.search.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.")

(defn get-landing-links
  ""
  [request]
  {:links [{:href "/site/collections/eosdis-landing-pages"
            :text "Landing Pages for EOSDIS Collections"}]})

(defn get-eosdis-landing-links
  ""
  [request]
  {})
