(ns cmr.mock-echo.client.echo-functionality
  "Contains functions for communicating with the mock echo api that aren't normal echo-rest
  operations"
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.string :as string]
            [clojure.set :as set]
            [clj-http.client :as client]
            [clj-time.format :as f]
            [cmr.common.util :as util]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]))
            
(defn request-options
  [conn]
  (merge
   (config/conn-params conn)
   {:accept :json
    :throw-exceptions false
    :headers {"Authorization" (config/echo-system-token)}
     ;; Overrides the socket timeout from conn-params
    :socket-timeout (config/echo-http-socket-timeout)}))

(defn post-options
  [conn body-obj]
  (merge (request-options conn)
         {:content-type :json
          :body (json/encode body-obj)}))

(defn unexpected-status-error!
  [status body]
  (errors/internal-error!
   ; Don't print potentially sensitive information
   (if (re-matches #".*token .* does not exist.*" body)
     (format "Unexpected status %d from response. body: %s" status "Token does not exist")
     (format "Unexpected status %d from response. body: %s" status (pr-str body)))))

(defn gateway-timeout-error!
  []
  (errors/throw-service-errors :gateway-timeout ["A gateway timeout occurred, please try your request again later."]))

(defn rest-post
  "Makes a post request to echo-rest. Returns a tuple of status, the parsed body, and the body."
  ([context url-path body-obj]
   (rest-post context url-path body-obj {}))
  ([context url-path body-obj options]
   (warn (format "Using legacy API call to POST %s!!!!!!!!!}" url-path))
   (let [conn (config/context->app-connection context :echo-rest)
         url (format "%s%s" (conn/root-url conn) url-path)
         params (if (some? (:form-params body-obj))
                  (merge (request-options conn) body-obj)
                  (merge (post-options conn body-obj) options))
         _ (warn (format "Using legacy API call to POST %s with params: %s" url params))
         response (client/post url params)
         {:keys [status body headers]} response
         parsed (when (.startsWith ^String (get headers "Content-Type" "") "application/json")
                  (json/decode body true))]
     [status parsed body])))

(defn rest-delete
  "Makes a delete request on echo-rest. Returns a tuple of status and body"
  ([context url-path]
   (rest-delete context url-path {}))
  ([context url-path options]
   (warn (format "Using legacy API call to DELETE %s!!!!!!!!!}" url-path))
   (let [conn (config/context->app-connection context :echo-rest)
         url (format "%s%s" (conn/root-url conn) url-path)
         params (merge (request-options conn) options)
         response (client/delete url params)
         {:keys [status body]} response]
     [status body])))

(defn cmr-sid->echo-sid
  "Converts a cmr style sid to an ECHO sid"
  [sid]
  (if (keyword? sid)
    {:sid {:user_authorization_type_sid
           {:user_authorization_type (-> sid name string/upper-case)}}}
    {:sid {:group_sid {:group_guid sid}}}))

(defn cmr-ace->echo-ace
  [ace]
  (let [{:keys [permissions group-guid user-type]} ace]
    (merge {:permissions (mapv (comp string/upper-case name) permissions)}
           (cmr-sid->echo-sid (or group-guid user-type)))))


(def ^:private echo-temporal-formatter
  "A clj-time formatter that can parse the times returned by ECHO in ACL temporal filters."
  (f/formatter "EEE MMM dd HH:mm:ss Z yyyy"))

(defn- generate-echo-temporal-date
  "Generates an ECHO temporal date from a clj-time date."
  [dt]
  (f/unparse echo-temporal-formatter dt))

(defn- cmr-temporal->echo-temporal
  [rt]
  (-> rt
      (update-in [:mask] csk/->SCREAMING_SNAKE_CASE_STRING)
      (update-in [:temporal-field] csk/->SCREAMING_SNAKE_CASE_STRING)
      (update-in [:start-date] generate-echo-temporal-date)
      (assoc :stop-date (generate-echo-temporal-date (:end-date rt)))
      (dissoc :end-date)))

(defn cmr-coll-id->echo-coll-id
  [cid]
  (when-let [{:keys [entry-titles access-value temporal]} cid]
    (merge {}
           (when entry-titles
             {:collection-ids (for [et entry-titles]
                                {:data-set-id et})})
           (when access-value
             {:restriction-flag
              (set/rename-keys access-value
                               {:include-undefined :include-undefined-value})})
           (when temporal
             {:temporal
              (cmr-temporal->echo-temporal temporal)}))))

(defn cmr-gran-id->echo-gran-id
  [gid]
  (when-let [{:keys [access-value temporal]} gid]
    (merge {}
           (when access-value
             {:restriction-flag
              (set/rename-keys access-value
                               {:include-undefined :include-undefined-value})})
           (when temporal
             {:temporal
              (cmr-temporal->echo-temporal temporal)}))))

(defn cmr-catalog-item-identity->cmr-catalog-item-identity
  [cid]
  (some-> cid
          (update-in [:collection-identifier] cmr-coll-id->echo-coll-id)
          (update-in [:granule-identifier] cmr-gran-id->echo-gran-id)
          util/remove-nil-keys))

(defn cmr-acl->echo-acl
  "Converts a cmr style acl back to the echo style. Converting echo->cmr->echo is lossy due to
  short names and version ids not being included. These are optional and don't impact enforcement
  so it's ok."
  [acl]
  (-> acl
      (update-in [:aces] (partial mapv cmr-ace->echo-ace))
      (update-in [:catalog-item-identity] cmr-catalog-item-identity->cmr-catalog-item-identity)
      (set/rename-keys {:guid :id :aces :access-control-entries})
      util/remove-nil-keys
      util/map-keys->snake_case
      (#(hash-map :acl %))))


(defn login
  "Logs into ECHO and returns the token"
  ([context username password]
   (login context username password "CMR Internal" "127.0.0.1"))
  ([context username password client-id user-ip-address]
   (let [token-info {:token {:username username
                             :password password
                             :client_id client-id
                             :user_ip_address user-ip-address}}
         _ (warn "Calling rest-post from login func")
         [status parsed body] (rest-post context "/tokens" token-info)
         _ (warn (format "called rest-post from login func, with status: %s body: %s parsed: %s" status body parsed))]
     (case status
       201 (get-in parsed [:token :id])
       504 (gateway-timeout-error!)

       ;; default
       (unexpected-status-error! status body)))))

(defn logout
  "Logs out of ECHO"
  [context token]
  (let [[status body] (rest-delete context (str "/tokens/" token))]
    (when-not (= 200 status)
      (unexpected-status-error! status body))))

(defn login-guest
  "Logs in as a guest and returns the token."
  [context]
  (login context "guest" "guest-password"))
