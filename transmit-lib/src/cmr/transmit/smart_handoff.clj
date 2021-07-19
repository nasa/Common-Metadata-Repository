(ns cmr.transmit.smart-handoff
  "This namespace handles retrieval of smart handoff resources."
  (:require
    [clj-http.client :as client]
    [cmr.common.log :as log :refer (debug info warn error)]
    [cmr.transmit.config :as config]
    [cmr.transmit.connection :as conn]))

(defn get-smart-handoff-schema
  "Returns the smart handoff schema with the given file name."
  [context schema-filename]
  (let [conn (config/context->app-connection context :smart-handoff)
        url (format "%s/%s" (conn/root-url conn) schema-filename)
        params (merge
                (config/conn-params conn)
                {:headers {:accept-charset "utf-8"}
                 :throw-exceptions false})
        start (System/currentTimeMillis)
        response (client/get url params)]
    (debug
     (format
      "Completed Get Smart Handoff Request to %s in [%d] ms" url (- (System/currentTimeMillis) start)))
    response))
