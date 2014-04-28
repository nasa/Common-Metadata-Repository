(ns cmr.common.services.messages
  "This namespace provides functions to generate messages used for error reporting, logging, etc.
   Messages used in more than one project should be placed here."
  (:require [clojure.string :as str]
            [cmr.common.services.errors :as errors]))

(defn data-error [error-type msg-fn & args]
  "Utility method that uses throw-service-error to generate a response with a specific status code
  and error message."
  (errors/throw-service-error error-type (apply msg-fn args)))

(defn invalid-msg
  "Creates a message saying that value does not conform to type."
  ([type value]
   (invalid-msg type value nil))
  ([type value context]
   (let [type-name (if (keyword? type)
                     (name type)
                     (str type))
         context-str (if context
                       (str " : " context)
                       "")]
     (format "[%s] is not a valid %s%s" (str value) type-name context-str))))