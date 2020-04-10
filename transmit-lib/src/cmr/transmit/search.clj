(ns cmr.transmit.search
  "Provide functions to invoke search app"
  (:require
   [clj-http.client :as client]
   [clojure.data.xml :as x]
   [cmr.common.api.context :as ch]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.common.xml :as cx]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]
   [ring.util.codec :as codec]))

(defn-timed find-granule-hits
  "Returns granule hits that match the given search parameters."
  [context params]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules")
        response (client/get request-url
                             (merge
                               (config/conn-params conn)
                               {:accept :xml
                                :query-params (assoc params :page-size 0)
                                :headers (assoc (ch/context->http-headers context)
                                                config/token-header (config/echo-system-token))
                                :throw-exceptions false}))
        {:keys [headers body]} response
        status (int (:status response))]
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
             :granule-ur (cx/string-at-path % [:name])
             :location (cx/string-at-path % [:location])}) ref-elems)))

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

(h/defsearcher search-for-variables :search
  (fn [conn]
    (format "%s/variables" (conn/root-url conn))))

(h/defsearcher search-for-services :search
  (fn [conn]
    (format "%s/services" (conn/root-url conn))))

(h/defsearcher search-for-subscriptions :search
  (fn [conn]
    (format "%s/subscriptions" (conn/root-url conn))))

