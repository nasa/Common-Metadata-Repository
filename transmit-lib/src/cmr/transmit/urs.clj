(ns cmr.transmit.urs
  (:require [cmr.transmit.connection :as conn]
            [cmr.transmit.config :as config]
            [ring.util.codec :as codec]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cmr.common.xml :as cx]
            [clojure.data.xml :as x]
            [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- get-user-url
  [conn username]
  (format "%s/users/%s" (conn/root-url conn) username))

(defn login-url
  [conn]
  (format "%s/login" (conn/root-url conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn user-exists?
  "Returns true if the given user exists in URS"
  [context user]
  (-> (h/request context :urs
                 {:url-fn #(get-user-url % user)
                  :method :get
                  :raw? true
                  :http-options {:basic-auth [(config/urs-username) (config/urs-password)]}})
      :status
      (= 200)))

(defn- login-xml
  "Creates the XML necessary to login to URS"
  [user password]
  (x/emit-str
   (x/element :login {}
              (x/element :username {} user)
              (x/element :password {} password))))

(defn- assert-login-response-successful
  "Checks that the login response from URS was successful. Throws a service error with the reason
  from URS if not."
  [body]
  (let [login-response (x/parse-str body)
        success? (cx/bool-at-path login-response [:success])]
    (when-not success?
      (errors/throw-service-error :unauthorized (cx/string-at-path login-response [:reason])))))

(defn login
  "Attempts to login to URS using the given username and password. Throws a service exception if login
  is unsuccessful."
  [context username password]
  (let [response (h/request context :urs
                            {:url-fn login-url
                             :method :post
                             :raw? true
                             :http-options {:basic-auth [(config/urs-username) (config/urs-password)]
                                            :body (login-xml username password)
                                            :throw-exceptions false
                                            :content-type mt/xml}})]
    ;; URS always returns a successful response code as long as the request was valid.
    (when-not (= (:status response) 200)
      (errors/internal-error!
       (format "Unexpected status code [%d] from URS login. Response Body: [%s]"
               (:status response) (:body response))))
    (assert-login-response-successful (:body response))))

(comment
 ;; Use this code to test with URS. Replace XXXX with real values
 (do
  (config/set-urs-username! "XXXX")
  (config/set-urs-password! "XXXX")
  (config/set-urs-protocol! "https")
  (config/set-urs-host! "XXXX")
  (config/set-urs-port! 443)
  (config/set-urs-relative-root-url! ""))

 (def context
   {:system (config/system-with-connections {} [:urs])})

 (user-exists? context "XXXX")
 (login context "XXXX" "XXX"))


