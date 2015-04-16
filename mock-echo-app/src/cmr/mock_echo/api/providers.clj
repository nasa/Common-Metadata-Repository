(ns cmr.mock-echo.api.providers
  "Defines the HTTP URL routes for providers"
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.mock-echo.data.provider-db :as provider-db]
            [cmr.mock-echo.api.api-helpers :as ah]))

(defn create-providers
  [context body]
  (let [providers (json/decode body true)]
    (provider-db/create-providers context providers)))

(defn get-providers
  [context]
  (let [providers (provider-db/get-providers context)]
    providers))

(defn build-routes [system]
  (routes
    (context "/providers" []
      ;; Create a bunch of providers all at once
      ;; This is used for adding test data.
      (POST "/" {params :params context :request-context body :body}
        (create-providers context (slurp body))
        {:status 201})

      ;; Mimics echo-rest retrieval of providers
      (GET "/" {context :request-context}
        (ah/status-ok (get-providers context))))))

