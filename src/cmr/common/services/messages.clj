(ns cmr.common.services.messages
  (:require [clojure.string :as str]
            [cmr.common.services.errors :as errors]))

(defn data-error [error-type msg-fn & args]
  (errors/throw-service-error error-type (apply msg-fn args)))