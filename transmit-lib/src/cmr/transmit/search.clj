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

(defn- parse-response-body
  "Parse the xml search response body and get the granule references"
  [body]
  (let [parsed  (x/parse-str body)
        ref-elems (cx/elements-at-path parsed [:references :reference])]
    (map (fn [ref-elem] (util/remove-nil-keys
                          {:id (cx/string-at-path ref-elem [:id])
                           :name (cx/string-at-path ref-elem [:name])})) ref-elems)))

(defn-timed find-granules-by-params
  "Find granules by by provider id, entry title and granule urs"
  [context params]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules.xml")
        response (client/post request-url
                              {:body (codec/form-encode params)
                               :content-type mt/form-url-encoded
                               :throw-exceptions false
                               :headers (assoc (ch/context->http-headers context)
                                               config/token-header (config/echo-system-token))
                               :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response]
    (if (= status 200)
      (parse-response-body body)
      (errors/internal-error!
        (format "Granule-id search failed. status: %s body: %s" status body)))))

