(ns cmr.transmit.search
  "Provide functions to invoke search app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cheshire.core :as cheshire]
            [cmr.system-trace.http :as ch]
            [cmr.transmit.connection :as conn]
            [cheshire.core :as json]
            [clojure.string :as str]
            [ring.util.codec :as codec]
            [cmr.common.mime-types :as mt]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.util :as util :refer [defn-timed]]))

(defn-timed find-granule-hits
  "Returns granule hits that match the given search parameters."
  [context params]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules")
        response (client/get request-url {:accept :xml
                                          :query-params (assoc params :page-size 0)
                                          :headers (assoc (ch/context->http-headers context)
                                                          config/token-header (config/echo-system-token))
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
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
                              {:body (codec/form-encode params)
                               :content-type mt/form-url-encoded
                               :throw-exceptions false
                               :headers (ch/context->http-headers context)
                               :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response]
    (if (= status 200)
      (parse-granule-response body)
      (errors/internal-error!
        (format "Granule search failed. status: %s body: %s" status body)))))

