(ns cmr.search.web.routes
  "This namespace is the one responsible for combining the routes intended for
  use by libraries and applications (API routes) and the routes intended for
  human consumption (site routes). It also provides routes that apply to both
  (e.g., robots.txt)."
  (:require
    [clojure.java.io :as io]
    [cmr.acl.core :as acl]
    [cmr.common.api.errors :as errors]
    [cmr.common.api.context :as context]
    [cmr.common-app.api.routes :as common-routes]
    [cmr.search.api.request-context-user-augmenter :as context-augmenter]
    [cmr.search.api.routes :as api-routes]
    [cmr.search.site.routes :as site-routes]
    [compojure.core :refer :all]
    [compojure.route :as route]
    [ring.middleware.keyword-params :as keyword-params]
    [ring.middleware.nested-params :as nested-params]
    [ring.middleware.params :as params]))

(defn copy-of-body-handler
  "Copies the body into a new attribute called :body-copy so that after a post
  of form content type the original body can still be read. The default ring
  params reads the body and parses it and we don't have access to it."
  [f]
  (fn [request]
    (let [^String body (slurp (:body request))]
      (f (assoc request
                :body-copy body
                :body (java.io.ByteArrayInputStream. (.getBytes body)))))))

(def robots-txt-response
  "Returns the robots.txt response."
  {:status 200
   :body (slurp (io/resource "public/robots.txt"))})

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      ;; Return robots.txt from the root /robots.txt and at the context (e.g.
      ;; /search/robots.txt)
      (GET "/robots.txt" req robots-txt-response)
      (context relative-root-url []
        (GET "/robots.txt" req robots-txt-response))
      (api-routes/build-routes system)
      (site-routes/build-routes system)
      (route/not-found "Not Found"))))

(defn handlers [system]
  (-> (build-routes system)
      ;; add-authentication-handler adds the token and client id for user to
      ;; the context add-user-id-and-sids-handler lazy adds the user id and
      ;; sids for that token Need to maintain this order (works backwards).
      context-augmenter/add-user-id-and-sids-handler
      acl/add-authentication-handler
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      errors/invalid-url-encoding-handler
      api-routes/mixed-arity-param-handler
      (errors/exception-handler api-routes/default-error-format-fn)
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      common-routes/pretty-print-response-handler
      params/wrap-params
      copy-of-body-handler))
