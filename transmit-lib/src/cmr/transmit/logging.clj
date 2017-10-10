(ns cmr.transmit.logging
  "This contains functions for interacting with the logging API."
  (:require
   [cheshire.core :as json]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- logging-url
  [conn]
  (format "%s/log" (conn/root-url conn)))

(defn- reset-logging-url
  [conn]
  (format "%s/log/reset" (conn/root-url conn)))

(defn get-logging-configuration
  "Returns the CMR logging configuration for the passed in application. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context {:keys [raw? http-options token]} application]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context application
                {:url-fn logging-url
                 :method :get
                 :raw? raw?
                 :http-options (merge {:headers headers
                                       :accept :json
                                       ;; :decompress-body false is needed to
                                       ;; workaround an issue with clj-http.client
                                       ;; not handling gzip and unicode characters
                                       :decompress-body false}
                                      http-options)}))))

(defn merge-logging-configuration
  "Returns the updated CMR logging configuration for the passed in application. The passed in
  content is the configuration updates that need to be merged into the current configuration.
  Valid options are:
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context content {:keys [raw? http-options token]} application]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context application
                {:url-fn logging-url
                 :method :put
                 :raw? raw?
                 :http-options (merge {:body content
                                       :content-type :edn
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn reset-logging-configuration
  "Resets and returns the reset CMR logging configuration for the passed in application.
  Valid options are:
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context {:keys [raw? http-options token]} application]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context application
                {:url-fn reset-logging-url
                 :method :get
                 :raw? raw?
                 :http-options (merge {:headers headers
                                       :accept :json
                                       ;; :decompress-body false is needed to
                                       ;; workaround an issue with clj-http.client
                                       ;; not handling gzip and unicode characters
                                       :decompress-body false}
                                      http-options)}))))
