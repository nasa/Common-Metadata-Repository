(ns cmr.system-int-test.utils.logging-util
  "This contains utilities for testing logging"
  (:require
   [clojure.test :refer [is]]
   [cmr.common-app.api.routes :as cr]
   [cmr.common.mime-types :as mt]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.transmit.logging :as trans-logging]))

(defn- process-response
  "Returns the response in a map for easy testing"
  [{:keys [status body content-type]}]
  (let [content-type-header (if (= :html content-type)
                               {cr/CONTENT_TYPE_HEADER mt/html}
                               {cr/CONTENT_TYPE_HEADER mt/edn})]
    (if (map? body)
      (assoc body :status status :headers content-type-header)
      {:status status
       :body body
       :headers content-type-header})))

(defn get-logging-configuration
  "Retrieves the logging configuration"
  ([application]
   (get-logging-configuration application nil nil))
  ([application token]
   (get-logging-configuration application token nil))
  ([application token options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (trans-logging/get-logging-configuration (s/context) options application)))))

(defn merge-logging-configuration
  "Merges the passed in configuration to the logging configuration of the passed in application.
   Returns the updated configuration to the caller."
  ([application config]
   (merge-logging-configuration application config nil nil))
  ([application config token]
   (merge-logging-configuration application config token nil))
  ([application config token options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (trans-logging/merge-logging-configuration (s/context) config options application)))))

(defn reset-logging-configuration
  "Resets the logging configuration"
  ([application]
   (get-logging-configuration application nil nil))
  ([application token]
   (get-logging-configuration application token nil))
  ([application token options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (trans-logging/reset-logging-configuration (s/context) options application)))))
