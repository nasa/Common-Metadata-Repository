(ns cmr.mock-echo.api.urs
  "Defines routes for mocking URS"
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.mime-types :as mt]
            [clojure.string :as str]
            [cmr.common.services.errors :as errors]
            [cmr.mock-echo.data.urs-db :as urs-db]
            [clojure.data.codec.base64 :as b64]))

(defn create-users
  "Processes a request to create multiple users."
  [context body]
  (let [users (json/decode body true)]
    (urs-db/create-users context users)))

(defn get-user
  "Processes a request to get a user."
  [context username]
  (if (urs-db/user-exists? context username)
    ;; The status code is the only thing the CMR cares about.
    {:status 200 :body "<user>...</user>"}
    {:status 404 :body "Not found.\n"}))

(def successful-login-response
  {:status 200
   :body "<LoginResponse><reason></reason><success>true</success><user>...</user></LoginResponse>"
   :headers {"content-type" mt/xml}})

(def unsuccessful-login-response
  {:status 200
   :body "<LoginResponse><reason>Invalid username or password</reason><success>false</success></LoginResponse>"
   :headers {"content-type" mt/xml}})

(defn parse-login-request
  "Parses a login request into a map of username and password"
  [body]
  (let [parsed (x/parse-str body)]
    {:username (cx/string-at-path parsed [:username])
     :password (cx/string-at-path parsed [:password])}))

(defn login
  "Processes a login request"
  [context body]
  (let [{:keys [username password]} (parse-login-request body)]
    (if (and (urs-db/user-exists? context username)
             (urs-db/password-matches? context username password))
      successful-login-response
      unsuccessful-login-response)))

(defn decode-base64
  [string]
  (-> string .getBytes b64/decode String.))

(defn decode-basic-auth
  "Decodes basic auth to a map of username and password. Based on
  https://github.com/remvee/ring-basic-authentication"
  [request]
  (let [auth (get-in request [:headers "authorization"])
        cred (and auth (decode-base64 (last (re-find #"^Basic (.*)$" auth))))
        [user pass] (and cred (str/split (str cred) #":" 2))]
    {:username user :password pass}))

(def URS_USERNAME "mock-urs-username")
(def URS_PASSWORD "mock-urs-password")

(defn assert-urs-basic-auth-info
  "URS expects that a basic auth client username and password is sent. This verifies that requests
  sent include this information."
  [request]
  (let [{:keys [username password]} (decode-basic-auth request)]
    (when-not (and (= username URS_USERNAME) (= password URS_PASSWORD))
      (errors/throw-service-error :unauthorized "Bad URS authentication info"))))

(defn build-routes [system]
  (routes
    (context "/urs" []

      (POST "/login" {:keys [request-context body] :as request}
        (assert-urs-basic-auth-info request)
        (login request-context (slurp body)))

      (context "/users" []

        ;; Create a bunch of users all at once
        ;; This is used for adding test data. Body should be a list of
        ;; maps with username and password
        (POST "/" {context :request-context body :body}
          (create-users context (slurp body))
          {:status 201})

        (context "/:username" [username]
          (GET "/" {:keys [request-context] :as request}
            (assert-urs-basic-auth-info request)
            (get-user request-context username)))))))




