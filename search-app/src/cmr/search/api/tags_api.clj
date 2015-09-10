(ns cmr.search.api.tags-api
  "Defines the API for tagging collections in the CMR."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.common.mime-types :as mt]

            [cmr.search.services.tagging-service :as tagging-service]))

;; TODO document the API in api_docs.md

(defn create-tag
  "TODO"
  [context params headers body]
  ;; TODO validate that content type is json
  ;; TODO validate that accept header is JSON (maybe)


  ;; TODO get user id from the token in params or headers
  ;; TODO parse the body
  ;; TODO validate body against a JSON schema

  ;; TODO at service level validate that only certain fields are passed in.

  ;; TODO create the tag
  (let [tag (json/parse-string (slurp body) true)
        response (tagging-service/create-tag context tag)]
    {:status 200
     :body (json/generate-string response)
     :headers {"Content-Type" mt/json}}))

(def tag-api-routes
  (context "/tags" []
    ;; Create a new tag
    (POST "/" {:keys [request-context body params headers]}
      (create-tag request-context params headers body))))


