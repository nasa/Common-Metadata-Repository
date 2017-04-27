(ns cmr.common-app.site.pages
  (:require
   [cmr.common-app.site.data :as data]
   [ring.util.response :as response]
   [selmer.parser :as selmer]))

(defn render-template
  "A utility function for preparing templates."
  [context template page-data]
  (response/response
   (selmer/render-file template page-data)))

(defn render-html
  "A utility function for preparing HTML templates."
  [context template page-data]
  (response/content-type
   (render-template context template page-data)
   "text/html"))

(defn render-xml
  "A utility function for preparing XML templates."
  [context template page-data]
  (response/content-type
   (render-template context template page-data)
   "text/xml"))

(defn not-found
  "Returns a route that always returns a 404 \"Not Found\" response using the
  EUI 404 template.

  Code adapted for use with Selmer from compojure.route/not-found."
  []
  (fn [request]
    (let [context (:request-context request)]
      (-> context
          (render-html "templates/not-found.html"
                       (data/base-page context "CMR Error Page"))
          (response/status 404)
          (cond-> (= (:request-method request) :head) (assoc :body nil))))))
