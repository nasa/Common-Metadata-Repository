(ns cmr.transmit.urs
  (:require
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as http-helper]
   [ring.util.codec :as codec]))

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

(defn- group-search-url
  [conn]
  (format "%s/api/user_groups/search" (conn/root-url conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn- get-bearer-token
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

(defn- request-with-auth
  "Retrieve CMR URS authentication info and submit request."
  [context request]
  (http-helper/request context :urs (update-in request
                                               [:http-options :headers]
                                               assoc
                                               "Authorization"
                                               (str "Bearer " (get-bearer-token context)))))

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
      (info (format "Cannot get info for username [%s] in URS. Failed with status code [%d].
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
  [context user-id]
  (let [{:keys [status body]} (request-with-auth context {:url-fn #(group-search-url %)
                                                          :method :get
                                                          :raw? true
                                                          :http-options {:query-params
                                                                         {:user_ids
                                                                          user-id}}})]
    (info "DEBUGz1" status)
    (info "DEBUGz2" (pr-str body))
    (info "DEBUGz3" user-id)
    (when-not (= 200 status)
      (info (format "Cannot get group info for username [%s] in URS. Failed with status code [%d].
        EDL error message: [%s]" user-id status (pr-str body)))
      (errors/throw-service-error
        :unauthorized
        (format "Cannot get group info for username [%s] in URS. Failed with status code [%d]."
                user-id status)))
    (map #(get % :name) body)))


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
 (login context "notexist" nil))
