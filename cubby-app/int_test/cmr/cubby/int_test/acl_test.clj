(ns cmr.cubby.int-test.acl-test
  (:require [clojure.test :refer :all]
            [cmr.transmit.cubby :as c]
            [cmr.cubby.int-test.utils :as u]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.common.util :as util]
            [clj-http.client :as client]))

(use-fixtures :once (u/int-test-fixtures))

(defn make-request
  "Makes a cubby request of the given type to the url given by the url-fn and with the token.
  Returns the raw clj-http response."
  [method url-fn token]
  (let [conn (config/context->app-connection (u/conn-context) :cubby)]
    (client/request
      {:url (url-fn conn)
       :method method
       :throw-exceptions false
       :headers (when token {config/token-header token})
       :connection-manager (conn/conn-mgr conn)})))

(defn permitted?
  [method url-fn token]
  (= 200 (:status (make-request method url-fn token))))

(defn not-permitted?
  [method url-fn token]
  (= 401 (:status (make-request method url-fn token))))


(deftest reset-acl-test
  (e/grant-group-admin (u/conn-context) "admin-read-group-guid" :read)
  (e/grant-group-admin (u/conn-context) "admin-update-group-guid" :update)

  (let [guest-token (e/login-guest (u/conn-context))
        user-token (e/login (u/conn-context) "user1" ["group-guid2" "group-guid3"])
        admin-read-token (e/login (u/conn-context) "admin" ["admin-read-group-guid" "group-guid3"])
        admin-update-token (e/login (u/conn-context) "admin" ["admin-update-group-guid" "group-guid3"])]


    (testing "not permitted tokens"
      (are [token]
             (not-permitted? :post c/reset-url token)

             nil
             guest-token
             user-token
             admin-read-token))

    (testing "permitted tokens"
      (is (permitted? :post c/reset-url admin-update-token)))))