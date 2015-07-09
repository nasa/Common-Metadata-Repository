(ns cmr.virtual-product.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [cmr.common-app.api.routes :as common-routes]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.common.mime-types :as mt]
            [cmr.virtual-product.services.virtual-product-service :as vps]
            [cheshire.core :as json]
            [cmr.virtual-product.services.health-service :as hs]))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/foo" []
        (GET "/" {params :params headers :headers context :request-context}
          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body "foo"}))

      (context "/keep-virtual" []
               (POST "/" {:keys [body content-type headers request-context]}
                     (cond
                       (= (mt/mime-type->format content-type) :json)
                       {:status 200
                        :body (vps/filter-virtual-granules request-context body)}

                       :else
                       {:status 415
                        :body (str "Unsupported content type [" content-type "]")})))

      (common-routes/health-api-routes hs/health))
    (route/not-found "Not Found")))


(defn- json-request? [request]
  (if-let [type (:content-type request)]
    (not (empty? (re-find #"^application/(.+\+)?json" type)))))

(defn- read-json [request]
  (if (json-request? request)
    (if-let [body (:body request)]
      (try
        [true (json/parse-string-strict (slurp body))]
        (catch com.fasterxml.jackson.core.JsonParseException ex
          [false nil])))))

(def malformed-response
  {:status  400
   :headers {"Content-Type" "application/json"}
   :body    (json/generate-string {"errors" ["Malformed JSON in request body"]})})

;; This middleware is a stripped down version of ring-json/wrap-json-body. There are a few
;; additional differences: This function uses json/parse-string-strict to parse the json in the
;; request body. The orginal function uses json/parse-string. If top level object of the json is
;; an array (which is the case for requests sent to keep-virtual end-point), json/parse-string will
;; parse it lazily which causes an issue if the input (inside the array) is an invalid json which
;; will be passed downstream as a valid json which otherwise should have been caught in an
;; exception (Is this a flaw in the langugage?! Or perhaps everything inside a try-catch block
;; should be forced for any effects). Also, this function modifies the output response
;; in case of a malformed json.
(defn- wrap-json-body
  "Middleware that parses the :body of JSON requests into a Clojure data
  structure."
  [handler]
  (fn [request]
    (if-let [[valid? json] (read-json request)]
        (if valid?
          (handler (assoc request :body json :content-type :json))
          malformed-response)
        (handler request))))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      handler/site
      wrap-json-body
      ring-json/wrap-json-response
      ))



