(ns cmr.search.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
    [cmr.search.site.data :as data]
    [selmer.parser :as selmer]))

(defn render-template-ok
  ""
  [template data]
  {:status 200
   :body (selmer/render-file template data)})

(defn home
  ""
  [request]
  (render-template-ok
    "templates/index.html"
    (data/get-index request)))

(defn landing-links
  ""
  [request]
  (render-template-ok
    "templates/landing-links.html"
    (data/get-landing-links request)))

(defn eosdis-landing-links
  ""
  [request]
  (render-template-ok
    "templates/eosdis-landing-links.html"
    (data/get-eosdis-landing-links request)))
