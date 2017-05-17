(ns cmr.transmit.humanizer
  "This contains functions for interacting with the humanizers API."
  (:require
   [cheshire.core :as json]
   [cmr.common.mime-types :as mt]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- humanizers-url
  [conn]
  (format "%s/humanizers" (conn/root-url conn)))

(defn update-humanizers
  "Create/update the humanizers through search app. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context content]
   (update-humanizers context content nil))
  ([context content {:keys [raw? http-options token]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn humanizers-url
                 :method :put
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn get-humanizers
  "Returns the humanizers configured in the metadata db. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context]
   (get-humanizers context nil))
  ([context {:keys [raw? http-options token]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn humanizers-url
                 :method :get
                 :raw? raw?
                 :http-options (merge {:headers headers
                                       :accept :json
                                       ;; :decompress-body false is needed to
                                       ;; workaround an issue with clj-http.client
                                       ;; not handling gzip and unicode characters
                                       :decompress-body false}
                                      http-options)}))))
