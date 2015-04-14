(ns cmr.cubby.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.cubby.data :as d]))

(defn- context->db
  "Returns the db in the context"
  [context]
  (get-in context [:system :db]))

(defn get-keys
  [context]
  {:status 200
   :content-type :json
   ;; The body is converted to a vector so that it will work with the ring-json middleware
   :body (vec (d/get-keys (context->db context)))})

(defn get-value
  [context key-name]
  (if-let [value (d/get-value (context->db context) key-name)]
    {:status 200 :body value}
    {:status 404}))

(defn set-value
  [context key-name value]
  (d/set-value (context->db context) key-name value)
  {:status 200})

(defn delete-value
  [context key-name]
  (d/delete-value (context->db context) key-name)
  {:status 200})

(defn reset
  [context]
  (d/reset (context->db context)))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/keys" []
        (GET "/" {context :request-context}
          (get-keys context))
        (context "/:key-name" [key-name]
          (GET "/" {context :request-context}
            (get-value context key-name))
          (PUT "/" {context :request-context body :body}
            (set-value context key-name (slurp body)))
          (DELETE "/" {context :request-context}
            (delete-value context key-name))))
      (POST "/reset" {context :request-context}
        ;; TODO enforce ACLs
        (reset context)))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



