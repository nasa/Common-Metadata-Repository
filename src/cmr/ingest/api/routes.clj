(ns cmr.ingest.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [cheshire.core :as cheshire]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.ingest.services.ingest :as ingest]
            [clojure.walk :as walk]
            [cmr.system-trace.http :as http-trace]))

(defn- build-routes [system]
  (routes
    (context "/providers/:provider-id" [provider-id]
      (context "/collections/:native-id" [native-id]
        (PUT "/" {:keys [body content-type request-context]}
          (let [metadata (string/trim (slurp body))
                concept {:metadata metadata
                         :format content-type
                         :provider-id provider-id
                         :native-id native-id
                         :concept-type :collection}]
            (r/response (ingest/save-concept request-context concept))))
        (DELETE "/" {:keys [request-context]}
          (let [concept-attribs {:provider-id provider-id
                                 :native-id native-id
                                 :concept-type :collection}]
            (r/response (ingest/delete-concept request-context concept-attribs))))))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))


