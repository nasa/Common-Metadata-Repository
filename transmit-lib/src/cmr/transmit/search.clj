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

(defn- parse-refs
  "Parse the xml search response body and get the references to the concepts"
  [body]
  (let [parsed  (x/parse-str body)
        ref-elems (cx/elements-at-path parsed [:references :reference])]
    (map (fn [ref-elem] (util/remove-nil-keys
                          {:id (cx/string-at-path ref-elem [:id])
                           :name (cx/string-at-path ref-elem [:name])})) ref-elems)))

(defn-timed find-granules-by-granule-urs
  "Return the corresponding granule id for each granule ur in granule-urs. The output is a map of
  granule-ur to the corresponding granule id"
  [context provider-id entry-title granule-urs]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules.xml")
        params {"provider-id[]" provider-id
                "entry_title" entry-title
                "granule_ur[]" (str/join "," granule-urs)}
        response (client/post request-url {:body (codec/form-encode params)
                                           :content-type mt/form-url-encoded
                                           :throw-exceptions false
                                           :headers (ch/context->http-headers context)
                                           :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response]
    (if (= status 200)
      (parse-refs body)
      (errors/internal-error!
        (format "Granule-id search failed. status: %s body: %s" status body)))))

