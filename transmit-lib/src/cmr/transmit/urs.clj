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
  ([context user]
   (user-exists? context user false))
  ([context user raw?]
   (let [response (h/request context :urs
                             {:url-fn #(get-user-url % user)
                              :method :get
                              :raw? true
                              :http-options {:basic-auth [(config/urs-username) (config/urs-password)]}})]
     (if raw?
       response
       (-> response :status (= 200))))))

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
  ([context username password]
   (login context username password false))
  ([context username password raw?]
   (let [response (h/request context :urs
                             {:url-fn login-url
                              :method :post
                              :raw? true
                              :http-options {:basic-auth [(config/urs-username) (config/urs-password)]
                                             :body (login-xml username password)
                                             :throw-exceptions false
                                             :content-type mt/xml}})]
     (if raw?
       response
       ;; URS always returns a successful response code as long as the request was valid.
       (if (= (:status response) 200)
         (assert-login-response-successful (:body response))
         (errors/internal-error!
          (format "Unexpected status code [%d] from URS login. Response Body: [%s]"
                  (:status response) (:body response))))))))

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





