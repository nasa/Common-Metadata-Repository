(ns cmr.search.routes
  "This namespace is the one responsible for combining the routes intended for
  use by libraries and applications (API routes) and the routes intended for
  human, browser, or web crawler consumption (site routes). It also provides
  routes that apply to both (e.g., robots.txt)."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cmr.acl.core :as acl]
   [cmr.common.api.errors :as errors]
   [cmr.common-app.api.request-context-user-augmenter :as context-augmenter]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.common.api.context :as cmr-context]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as svc-errors]
   [cmr.search.api.routes :as api-routes]
   [cmr.search.services.messages.common-messages :as msg]
   [cmr.search.site.routes :as site-routes]
   [compojure.core :refer [GET context routes]]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn find-query-str-mixed-arity-param
  "Return the first parameter that has mixed arity, i.e., appears with both single and multivalued in
  the query string. e.g. foo=1&foo[bar]=2 is mixed arity, so is foo[]=1&foo[bar]=2. foo=1&foo[]=2 is
  not. Parameter with mixed arity will be flagged as invalid later."
  [query-str]
  (when query-str
    (let [query-str (-> query-str
                        (str/replace #"%5B" "[")
                        (str/replace #"%5D" "]")
                        (str/replace #"\[\]" ""))]
      (last (some #(re-find % query-str)
                  [#"(^|&)(.*?)=.*?&\2\["
                   #"(^|&)(.*?)\[.*?&\2="])))))

(defn mixed-arity-param-handler
  "Detect query string with mixed arity and throws a 400 error. Mixed arity param is when a single
  value param is mixed with multivalue. One specific case of this is for improperly expressed options
  in the query string, e.g., granule_ur=*&granule_ur[pattern]=true. Ring parameter handling throws
  500 error when it happens. This middleware handler returns a 400 error early to avoid the 500 error
  from Ring."
  [handler]
  (fn [request]
    (when-let [mixed-param (find-query-str-mixed-arity-param (:query-string request))]
      (svc-errors/throw-service-errors
       :bad-request
       [(msg/mixed-arity-parameter-msg mixed-param)]))
    (handler request)))

(defn copy-of-body-handler
  "Copies the body into a new attribute called :body-copy so that after a post
  of form content type the original body can still be read. The default ring
  params reads the body and parses it and we don't have access to it."
  [handler]
  (fn [request]
    (let [^String body (slurp (:body request))]
      (handler (assoc
                request
                :body-copy body
                :body (java.io.ByteArrayInputStream. (.getBytes body)))))))

(defn default-error-format
  "Determine the format that errors should be returned in based on the request URI."
  [{:keys [uri]} _e]
  (if (or (re-find #"/caches" uri)
          (re-find #"/keywords" uri))
    mt/json
    mt/xml))

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
      (context
        relative-root-url []
        (GET "/robots.txt" req robots-txt-response))
      (api-routes/build-routes system)
      (site-routes/build-routes system)
      (common-pages/not-found))))

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
      mixed-arity-param-handler
      (errors/exception-handler default-error-format)
      common-routes/add-request-id-response-handler
      (cmr-context/build-request-context-handler system)
      common-routes/pretty-print-response-handler
      params/wrap-params
      copy-of-body-handler))
