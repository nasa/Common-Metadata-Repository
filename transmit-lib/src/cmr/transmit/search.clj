(ns cmr.transmit.search
  "Provide functions to invoke search app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cmr.transmit.http-helper :as h]
            [cheshire.core :as cheshire]
            [cmr.common.api.context :as ch]
            [cmr.transmit.connection :as conn]
            [cheshire.core :as json]
            [clojure.string :as str]
            [ring.util.codec :as codec]
            [cmr.common.mime-types :as mt]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.util :as util :refer [defn-timed]]))

(defn- humanizer-url
  [conn]
  (format "%s/humanizer" (conn/root-url conn)))

(defn-timed find-granule-hits
  "Returns granule hits that match the given search parameters."
  [context params]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules")
        response (client/get request-url (merge
                                           (config/conn-params conn)
                                           {:accept :xml
                                            :query-params (assoc params :page-size 0)
                                            :headers (assoc (ch/context->http-headers context)
                                                            config/token-header (config/echo-system-token))
                                            :throw-exceptions false}))
        {:keys [status headers body]} response]
    (case status
      200 (Integer/parseInt (get headers "CMR-Hits"))
      ;; default
      (errors/internal-error!
        (format "Granule search failed. status: %s body: %s"
                status body)))))

(defn- parse-granule-response
  "Parse xml search response body and return the granule references"
  [xml]
  (let [parsed (x/parse-str xml)
        ref-elems (cx/elements-at-path parsed [:references :reference])]
    (map #(util/remove-nil-keys
            {:concept-id (cx/string-at-path % [:id])
             :granule-ur (cx/string-at-path % [:name])}) ref-elems)))

(defn-timed find-granule-references
  "Find granules by parameters in a post request. The function returns an array of granule
  references, each reference being a map having concept-id and granule-ur as the fields"
  [context params]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules.xml")
        response (client/post request-url
                              (merge
                                (config/conn-params conn)
                                {:body (codec/form-encode params)
                                 :content-type mt/form-url-encoded
                                 :throw-exceptions false
                                 :headers (ch/context->http-headers context)}))
        {:keys [status body]} response]
    (if (= status 200)
      (parse-granule-response body)
      (errors/internal-error!
        (format "Granule search failed. status: %s body: %s" status body)))))

(defn get-humanizer
  "Returns the humanizer configured in the metadata db. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context]
   (get-humanizer context nil))
  ([context {:keys [raw? http-options token]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn humanizer-url
                 :method :get
                 :raw? raw?
                 :http-options (merge {:accept :json
                                       :headers headers}
                                      http-options)}))))


