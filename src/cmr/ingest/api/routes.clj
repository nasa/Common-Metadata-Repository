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
            [clojure.walk :as walk]))

(defn- build-routes [system]
  (routes
    (context "/providers" []
             (context "/:provider-id" [provider-id] 
                      (routes
                        (context "/collections" [] 
                                 (routes
                                   (context "/:native-id" [native-id]
                                            (PUT "/" params
                                                 (let [metadata (string/trim (slurp (:body params)))
                                                       format (:content-type params)
                                                       concept {:metadata metadata 
                                                                :format format
                                                                :provider-id provider-id 
                                                                :native-id native-id
                                                                :concept-type :collection}]
                                                   (r/response (ingest/save-concept system concept))))
                                            (DELETE "/" []
                                                    (let [concept-attribs {:provider-id provider-id 
                                                                           :native-id native-id
                                                                           :concept-type :collection}]
                                                      (r/response (ingest/delete-concept system concept-attribs)))))))))
             (route/not-found "Not Found"))))

(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))


