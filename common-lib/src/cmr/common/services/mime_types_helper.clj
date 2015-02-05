(ns cmr.common.services.mime-types-helper
  "This namespace provides service functions for working with mime-types."
  (:require [cmr.common.services.errors :as errors]))

(defn validate-request-mime-type
  "Validates the requested mime type is supported."
  [mime-type supported-types]
  (when-not (get supported-types mime-type)
    (errors/throw-service-error
      :bad-request (format "The mime type [%s] is not supported." mime-type))))