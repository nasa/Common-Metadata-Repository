(ns cmr.common.api.errors
  (:require [cmr.common.log :refer [error]]))

(def type->http-status-code
  {:not-found 404
   :bad-request 400
   :invalid-data 422})

(defn exception-handler
  "A ring exception handler that will handle errors thrown by the cmr.common.services.errors
  functions."
  [f]
  (fn [request]
    (try (f request)
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [type errors]} (ex-data e)]
          {:status (type->http-status-code type)
           :headers {"Content-Type" "application/json"}
           :body {:errors errors}}))
      (catch Exception e
        (error e)
        {:status 500 :body "An Internal Error has occurred."}))))