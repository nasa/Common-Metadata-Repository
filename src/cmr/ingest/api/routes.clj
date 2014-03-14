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
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.ingest.services.ingest :as ingest]))

(defn- save-concept
  "Store a concept and return the revision"
  [system concept]
  (let [revision-id (ingest/save-concept system concept)]
    (println concept)
    {:status 201
     :body {:revision-id revision-id}
     :headers {"Content-Type" "json"}}))


(defn- build-routes [system]
  (routes
    (context "/providers" []
             (GET "/" []
                  {:status 200
                   :headers {"Content-Type" "text/plain"}
                   :body "checkout CMR Ingest API at http://fix.me.later"})
             (context "/:provider-id" [provider-id] 
                      (routes
                        (context "/collections" [] 
                                 (routes
                                   (context "/:native-id" [native-id]
                                            (PUT "/" params
                                                 (save-concept system 
                                                               (assoc  (:body params) 
                                                                 :provider-id provider-id 
                                                                 :native-id native-id
                                                                 :concept-type :collections)))))))))
    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))


