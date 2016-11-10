(ns cmr.transmit.community-usage-metrics
  "This contains functions for interacting with the humanizers API."
  (:require
   [cheshire.core :as json]
   [cmr.common.mime-types :as mt]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- community-usage-metrics-url
  [conn]
  (format "%s/community-usage-metrics" (conn/root-url conn)))

(defn update-community-usage-metrics
  "Create/update the community usage metrics through search app. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context content]
   (update-community-usage-metrics context content nil))
  ([context content {:keys [raw? http-options token]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn community-usage-metrics-url
                 :method :put
                 :raw? raw?
                 :http-options (merge {:body content
                                       :content-type (mt/format->mime-type :csv)
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn get-community-usage-metrics
  "Returns the community usage metrics configured in the metadata db. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context]
   (get-community-usage-metrics context nil))
  ([context {:keys [raw? http-options token]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn community-usage-metrics-url
                 :method :get
                 :raw? raw?
                 :http-options (merge {:headers headers
                                       :accept :json}
                                      http-options)}))))
