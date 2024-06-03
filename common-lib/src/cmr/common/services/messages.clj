(ns cmr.common.services.messages
  "This namespace provides functions to generate messages used for error reporting, logging, etc.
   Messages used in more than one project should be placed here."
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]))

(defn data-error
  "Utility method that uses throw-service-error to generate a response with a specific status code
   and error message."
  [error-type msg-fn & args]
  (errors/throw-service-error error-type (apply msg-fn args)))

(defn invalid-msg
  "Creates a message saying that value does not conform to type."
  ([date-type value]
   (invalid-msg date-type value nil))
  ([date-type value context]
   (let [type-name (if (keyword? date-type)
                     (name date-type)
                     (str date-type))
         context-str (if context
                       (str " : " context)
                       "")]
     (format "[%s] is not a valid %s%s"
             (util/html-escape (str value))
             (util/html-escape type-name)
             context-str))))

(defn invalid-numeric-range-msg
  "Creates a message saying the range string does not have the right format."
  [input-str]
  (format (str "[%s] is not of the form 'value', 'min-value,max-value', 'min-value,', or "
               "',max-value' where value, min-value, and max-value are optional numeric values.")
          (util/html-escape input-str)))

(defn invalid-date-range-msg
  "Creates a message saying the range string does not have the right format."
  [input-str]
  (format (str "[%s] is not of the form 'value', 'min-value,max-value', 'min-value,', or "
               "',max-value' where value, min-value, and max-value are optional date-time values.")
          (util/html-escape input-str)))

(defn invalid-native-id-msg
  "Creates a message stating that no concept exists for the provided native-id and provider-id."
  [concept-type provider-id native-id]
  (format "%s with native id [%s] in provider [%s] does not exist."
          (util/html-escape (csk/->PascalCaseString concept-type))
          (util/html-escape native-id)
          (util/html-escape provider-id)))
