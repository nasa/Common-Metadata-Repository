(ns cmr.cubby.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.cubby.data :as d]
            [cmr.common.cache :as cache]
            [cmr.acl.core :as acl]
            [cmr.common-app.api.routes :as common-routes]
            [cmr.elastic-utils.connect :as es-conn]
            [cmr.transmit.echo.rest :as echo-rest]))

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
  (if (d/delete-value (context->db context) key-name)
    {:status 200}
    {:status 404
     :content-type :json
     :errors [(format "No cached value with key [%s] was found"
                      key-name)]}))

(defn delete-all-values
  [context]
  (d/delete-all-values (context->db context))
  {:status 200})

(defn reset
  [context]
  (cache/reset-caches context)
  (d/reset (context->db context)))

(defn health
  "Returns the health state of the app."
  [context]
  (let [elastic-health (es-conn/health context :db)
        echo-rest-health (echo-rest/health context)
        ok? (and (:ok? elastic-health) (:ok? echo-rest-health))]
    {:ok? ok?
     :dependencies {:elastic_search elastic-health
                    :echo echo-rest-health}}))

(def key-routes
  (context "/keys" []
    (GET "/" {context :request-context}
      (get-keys context))
    (DELETE "/" {context :request-context}
      (delete-all-values context))
    (context "/:key-name" [key-name]
      (GET "/" {context :request-context}
        (get-value context key-name))
      (PUT "/" {context :request-context body :body}
        (set-value context key-name (slurp body)))
      (DELETE "/" {context :request-context}
        (delete-value context key-name)))))

(def admin-routes
  (POST "/reset" {:keys [request-context params headers]}
    (acl/verify-ingest-management-permission request-context :update)
    (reset request-context)))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      admin-routes
      (common-routes/health-api-routes health)
      key-routes)
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      (http-trace/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



