(ns cmr.transmit.urs
  (:require
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.log :refer [error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as http-helper]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- get-user-url
  [conn username]
  (format "%s/api/users/verify_uid?uid=%s" (conn/root-url conn) username))

(defn- login-application-url
  [conn]
  (format "%s/oauth/token?grant_type=client_credentials" (conn/root-url conn)))

(defn- user-info-url
  [conn username]
  (format "%s/api/users/%s" (conn/root-url conn) username))

(defn- groups-for-user-url
  [conn username]
  (format "%s/api/user_groups/groups_for_user/%s" (conn/root-url conn) username))

(defn- launchpad-validation-url
  [conn]
  (format "%s/api/nams/edl_user_uid" (conn/root-url conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URS cache functions

(def urs-cache-key
  "The cache key for a URS cache."
  :urs)

(def URS_CACHE_TIME
  "The number of milliseconds CMR client bearer token information will be cached."
  (* 24 60 60 1000))

(defn create-urs-cache
  "Creates a cache for which CMR client bearer tokens are stored in memory."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl URS_CACHE_TIME}))

(defn- get-bearer-token-fn
  "Get CMR_SSO_APP token from EDL."
  [context]
  (let [{:keys [status body]} (http-helper/request
                               context
                               :urs
                               {:url-fn login-application-url
                                :method :post
                                :raw? true
                                :http-options {:basic-auth
                                               [(config/urs-username)
                                                (config/urs-password)]}})]
    (when-not (= 200 status)
      (errors/internal-error!
       (format "Cannot get CMR_SSO_APP token in EDL. Failed with status code [%d]."
               status)))
    (:access_token body)))

(defn get-bearer-token
  "Get the CMR client bearer token from from the cache."
  [context]
  (if-let [cache (cache/context->cache context urs-cache-key)]
    (cache/get-value cache :cmr (fn [] (get-bearer-token-fn context)))
    (get-bearer-token-fn context)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn- request-with-auth
  "Retrieve CMR URS authentication info and submit request."
  [context request]
  (http-helper/request context :urs (update-in request
                                               [:http-options :headers]
                                               assoc
                                               "Authorization"
                                               (str "Bearer " (get-bearer-token context)))))

(defn get-launchpad-user
  "Returns URS user info associated with a launchpad token"
  [context token]
  (let [{:keys [status body]} (request-with-auth context {:url-fn #(launchpad-validation-url %)
                                                          :method :post :raw? true
                                                          :http-options {:form-params {:token token}}})]
    (when-not (= 200 status)
      (errors/throw-service-error
       :unauthorized
       (format "Cannot get info for Launchpad token (partially redacted) [%s] in URS. Failed with status code [%d]. EDL error message: [%s]"
               (common-util/scrub-token token)
               status
               (pr-str body))))
    ;; The throw above will prevent the next line of code from executing if status is not 200.
    ;; The block below is protected if Earthdata Login (EDL) goes down for any reason and the body
    ;; is not what is expected; CMR-10086.
    (try
      (select-keys body [:lp_token_expires_in :uid])
      (catch Exception _e
        (if (string? body)
          (errors/throw-service-error
           :unauthorized
           (format "Cannot get info for Launchpad token (partially redacted) [%s] in URS. Failed with status code [%d]. EDL error message: [%s]"
                   (common-util/scrub-token token)
                   status
                   (pr-str (common-util/trunc body 500)))) ;the body is truncated just in-case it is really big.
          (errors/throw-service-error
           :unauthorized
           (format "Cannot get info for Launchpad token (partially redacted) [%s] in URS. Failed with status code [%d]."
                   (common-util/scrub-token token)
                   status)))))))

(defn user-exists?
  "Returns true if the given user exists in URS"
  ([context user]
   (user-exists? context user false))
  ([context user raw?]
   (let [response (request-with-auth context {:url-fn #(get-user-url % user)
                                              :method :get
                                              :raw? raw?})]
     (if raw?
       response
       (not (nil? response))))))

(defn get-user-info
  "Returns URS info associated with a username"
  [context user]
  (let [{:keys [status body]} (request-with-auth context {:url-fn #(user-info-url % user)
                                                          :method :get
                                                          :raw? true})]
    (when-not (= 200 status)
      (error (format "Cannot get info for username [%s] in URS. Failed with status code [%d].
        EDL error message: [%s]" user status (pr-str body)))
      (errors/throw-service-error
        :unauthorized
        (format "Cannot get info for username [%s] in URS. Failed with status code [%d]."
                user status)))

    body))

(defn get-user-email
  "Returns URS email associated with a username"
  [context user]
  (:email_address (get-user-info context user)))

(defn get-edl-groups-by-username
  "Returns groups associated with a username"
  [context username]
  (let [{:keys [status body]} (request-with-auth context {:url-fn #(groups-for-user-url % (name username))
                                                          :method :get
                                                          :raw? true})]
    (when-not (= 200 status)
      (error (format "Cannot get group info for username [%s] in URS. Failed with status code [%d].
        EDL error message: [%s]" username status (pr-str body)))
      (errors/throw-service-error
        :unauthorized
        (format "Cannot get group info for username [%s] in URS. Failed with status code [%d]."
                username status)))
    (map :group_id (:user_groups body))))

(comment
 ;; Use this code to test with URS. Replace XXXX with real values
 (do
  (config/set-urs-username! "XXXX")
  (config/set-urs-password! "XXXX")
  (config/set-urs-protocol! "https")
  (config/set-urs-host! "XXXX")
  (config/set-urs-port! 443)
  (config/set-urs-relative-root-url! ""))
 (do
  (config/set-urs-port! 4008)
  (def context
    {:system (config/system-with-connections {} [:urs])})
  (get-edl-groups-by-username context username)

  (require '[cmr.mock-echo.client.mock-urs-client :as mock-urs])

  (mock-urs/create-users context [{:username "foo" :password "foopass"}
                                  {:username "jason" :password "jasonpass"}]))

 (-> user/mock-echo-system :urs-db deref)

 (user-exists? context "notexist")
 (user-exists? context "foo")
 (user-exists? context "jason")
 (login context "foo" "badpass")
 (login context "foo" "jasonpass")
 (login context "foo" "foopass")
 (login context "notexist" "foopass")
 (login context "notexist" "")
 (login context "notexist" nil)

 ;; when REPL launched with non-local connection configs
 (def context
   {:system (config/system-with-connections {} [:urs])})
 (config/set-urs-relative-root-url! "")
 (def lptoken "<YOUR LAUNCHPAD TOKEN HERE>")
 (get-launchpad-user context "badtoken")
 (get-launchpad-user context lptoken))
