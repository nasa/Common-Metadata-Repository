(ns cmr.mock-echo.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.mock-echo.data.token-db :as token-db]))


(defn login
  [context body]
  (let [{info :token} (json/decode body true)]
    (token-db/create context info)))

(defn- build-routes [system]
  (routes
    (POST "/reset" []
      ;; TODO reset on any databases to clear all state
      {:status 200
       :body ""})

    (context "/tokens" []
      ;; Login
      (POST "/" {params :params headers :headers context :request-context body :body}
        ;; TODO
        ;; Post json token information to login
        ;; Should return a new token each time this is called.
        ;; The token is returned in a header called Location

        (let [token (login context (slurp body))
              url (str "http://localhost:3000/tokens/" (:id token))]
          {:status 201
           :content-type :json
           :headers {"Location" url}
           :body {:token token}}))
      (context "/:token-id" [token-id]
        ;; Logout
        (DELETE "/"
          ;; TODO
          {:status 200
           :body ""}
          )
        ;; Get token info
        (GET "/"
          ;; TODO return token information
          {:status 200
           :body ""}
          )))

    (context "/foo" []
      (GET "/" {params :params headers :headers context :request-context}
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body "foo"}))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-response))



