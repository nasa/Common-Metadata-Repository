(ns cmr.common.api.errors
  (:require [cmr.common.log :refer [error]]
            [clojure.data.xml :as x]))

(def type->http-status-code
  {:not-found 404
   :bad-request 400
   :unauthorized 401
   :invalid-data 422
   :conflict 409})

(def internal-error-ring-response
  {:status 500 :body {:errors ["An Internal Error has occurred."]} :content-type :json})

(defn- response-type-body
  "Returns the response content-type and body for the given errors and format"
  [errors xml-format?]
  (let [content-type (if xml-format? "application/xml" "application/json")
        body (if xml-format?
               (x/emit-str
                 (x/element :errors {}
                            (for [err errors]
                              (x/element :error {} err))))
               {:errors errors})]
    [content-type body]))

(defn exception-handler
  "A ring exception handler that will handle errors thrown by the cmr.common.services.errors
  functions."
  [f]
  (fn [request]
    (try (f request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (:type data)
            (let [accept-format (get-in request [:headers "accept"])
                  xml-format? (when accept-format (re-find #"xml" accept-format))
                  {:keys [type errors]} data
                  status-code (type->http-status-code type)
                  [content-type response-body] (response-type-body errors xml-format?)]
              {:status status-code
               :headers {"Content-Type" content-type}
               :body response-body})
            (do
              (error e)
              internal-error-ring-response))))
      (catch Throwable e
        (error e)
        internal-error-ring-response))))
