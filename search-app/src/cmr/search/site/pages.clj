(ns cmr.search.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
    [cmr.search.site.data :as data]
    [selmer.parser :as selmer]))

(defn render-template-ok
  "A utility function for preparing template pages."
  [template data]
  {:status 200
   :body (selmer/render-file template data)})

(defn home
  "Prepar the home page template."
  [request]
  (render-template-ok
    "templates/index.html"
    (data/get-index request)))

(defn landing-links
  "Prepare the page that links to all landing page links.

  For now, this is just a page with a single link (the EOSDIS collections
  landing pages)."
  [request]
  (render-template-ok
    "templates/landing-links.html"
    (data/get-landing-links request)))

(defn eosdis-landing-links
  "Prepare the page that provides links to all the EOSDIS landing pages."
  [request]
  (render-template-ok
    "templates/eosdis-landing-links.html"
    (data/get-eosdis-landing-links request)))
