(ns cmr.graph.rest.response
  (:require
   [cheshire.core :as json]
   [ring.util.http-response :as response]
   [taoensso.timbre :as log]))

(defn json
  [_request data]
  (-> data
      json/generate-string
      response/ok
      (response/content-type "application/json")))

(defn not-found
  [_request]
  (response/content-type
   (response/not-found "Not Found")
   "plain/text"))
