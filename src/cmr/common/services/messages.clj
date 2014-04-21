(ns cmr.common.services.messages
  "This namespace provides functions to generate messages used for error reporting, logging, etc.
   Messages used in more than one project should be placed here."
  (:require [clojure.string :as str]
            [cmr.common.services.errors :as errors]))

(defn data-error [error-type msg-fn & args]
  "Utility method that uses throw-service-error to generate a response with a specific status code
  and error message."
  (errors/throw-service-error error-type (apply msg-fn args)))