(ns cmr.transmit.search
  "Provide functions to invoke search app"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.data.xml :as xml]
   [cmr.common.api.context :as ch]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.common.xml :as cx]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as hh]
   [ring.util.codec :as codec]))

(defn token-header
 [context]
 (assoc (ch/context->http-headers context)
   config/token-header (config/echo-system-token)))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn-timed save-subscription-notification-time
 "make an http call to the database application"
 [context sub-id last-notified-time]
 (let [conn (config/context->app-connection context :metadata-db)
       request-url (str (conn/root-url conn) (format "/subscription/%s/notification-time" sub-id))
       request-body {:last-notified-time last-notified-time}
       params (merge
               (config/conn-params conn)
               {:accept :xml
                :headers (merge
                          (token-header context)
                          {:client-id config/cmr-client-id})
                :throw-exceptions false
                :body (json/generate-string request-body)})
       response (client/put request-url params)
       {:keys [body]} response
       status (int (:status response))]
   (when-not (= status 204)
     (errors/internal-error!
       (format "Subscription update failed. status: %s body: %s" status body)))))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn-timed find-granule-hits
  "Returns granule hits that match the given search parameters."
  [context params]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules")
        params (merge
                (config/conn-params conn)
                {:accept :xml
                 :query-params (assoc params :page-size 0)
                 :headers (assoc (ch/context->http-headers context)
                                 config/token-header (config/echo-system-token)
                                 :client-id config/cmr-client-id)
                 :throw-exceptions false})
        response (client/get request-url params)
        {:keys [headers body]} response
        status (int (:status response))]
    (case status
      200 (Long/parseLong (get headers "CMR-Hits"))
      ;; default
      (errors/internal-error!
        (format "Granule search failed. status: %s body: %s"
                status body)))))

(defn- parse-granule-response
  "Parse xml search response body and return the granule references"
  [xml]
  (let [parsed (xml/parse-str xml)
        ref-elems (cx/elements-at-path parsed [:references :reference])]
    (map #(util/remove-nil-keys
            {:concept-id (cx/string-at-path % [:id])
             :granule-ur (cx/string-at-path % [:name])
             :location (cx/string-at-path % [:location])}) ref-elems)))

(defn parse-collection-response
  "Parse xml search response body and return the collection references"
  [xml]
  (let [parsed (xml/parse-str xml)
        ref-elems (cx/elements-at-path parsed [:references :reference])]
    (map #(util/remove-nil-keys
            {:concept-id (cx/string-at-path % [:id])
             :name (cx/string-at-path % [:name])
             :location (cx/string-at-path % [:location])}) ref-elems)))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn-timed find-concept-references
  "Find granules by parameters in a post request. The function returns an array of granule
  references, each reference being a map having concept-id and granule-ur as the fields"
  [context params concept-type]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) (format "/%ss.xml" (name concept-type)))
        request-body (dissoc params :token)
        token (:token params)
        header (merge (ch/context->http-headers context)
                      {:client-id config/cmr-client-id})
        params (merge
                (config/conn-params conn)
                {:body (codec/form-encode request-body)
                 :content-type mt/form-url-encoded
                 :throw-exceptions false
                 :headers (if token (assoc header config/token-header token) header)})
        response (client/post request-url params)
        {:keys [status body]} response]
    (if (= status 200)
      (parse-granule-response body)
      (errors/internal-error!
        (format "Granule search failed. status: %s body: %s" status body)))))

(declare validate-search-params context params concept-type)
(defn-timed validate-search-params
  "Attempts to search granules using given params via a POST request. If the response contains a
  non-200 http code, returns the response body."
  [context params concept-type]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) (case concept-type
                                                :collection "/collections.json"
                                                :granule "/granules.json"))
        request-body (-> params
                         (dissoc :token)
                         (assoc :page_size 0))
        token (:token params)
        header (merge
                (ch/context->http-headers context)
                {:client-id config/cmr-client-id})
        params (merge
                (config/conn-params conn)
                {:body (codec/form-encode request-body)
                 :content-type mt/form-url-encoded
                 :throw-exceptions false
                 :headers (if token (assoc header config/token-header token) header)})
        response (client/post request-url params)
        {:keys [status body]} response]
    (when-not (= status 200)
      body)))

(declare search-for-collections)
(hh/defsearcher search-for-collections :search
  (fn [conn]
    (format "%s/collections" (conn/root-url conn))))

(declare search-for-variables)
(hh/defsearcher search-for-variables :search
  (fn [conn]
    (format "%s/variables" (conn/root-url conn))))

(declare search-for-services)
(hh/defsearcher search-for-services :search
  (fn [conn]
    (format "%s/services" (conn/root-url conn))))

(declare search-for-tools)
(hh/defsearcher search-for-tools :search
  (fn [conn]
    (format "%s/tools" (conn/root-url conn))))

(declare search-for-subscriptions)
(hh/defsearcher search-for-subscriptions :search
  (fn [conn]
    (format "%s/subscriptions" (conn/root-url conn))))
