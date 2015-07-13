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

(defn-timed find-granule-ids
  "Return the corresponding granule id for each granule ur in granule-urs. The output is a map of
  granule-ur to the corresponding granule id"
  [context provider-id entry-title granule-urs]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules.json")
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
      (let [gran-refs (get-in (json/parse-string (:body response) true) [:feed :entry])]
        (reduce #(assoc %1 (:title %2)(:id %2)) {} gran-refs))
      (errors/internal-error!
        (format "Granule-id search failed. status: %s body: %s" status body)))))

