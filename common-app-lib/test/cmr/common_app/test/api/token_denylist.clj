(ns cmr.common-app.test.api.token-denylist
  (:require
   [clojure.test :refer [deftest is]]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common-app.api.token-denylist :as token-denylist]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.token-denylist :as denylist]
   [cmr.transmit.tokens :as tokens]))

(def ^:private user-token (apply str (repeat 120 "t")))

(defn- error-type
  [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (:type (ex-data e)))))

(deftest verify-token-allowed-test
  (with-redefs [denylist/denied? (fn [_ _ user-id-fn] (= "user1" (user-id-fn)))
                tokens/get-user-id (constantly "user1")]
    (is (= :unauthorized (error-type #(token-denylist/verify-token-allowed {:token user-token}))))
    (is (nil? (token-denylist/verify-token-allowed {})))
    (is (nil? (token-denylist/verify-token-allowed {:token (transmit-config/echo-system-token)})))
    ;; Legacy tokens resolve through a call that logs the token, so they are never looked up.
    (is (nil? (token-denylist/verify-token-allowed {:token "short-legacy-token"})))))

(deftest add-entry-route-test
  (let [added (atom nil)]
    (with-redefs [acl/verify-ingest-management-permission (constantly nil)
                  lt-validation/validate-write-token (constantly nil)
                  denylist/add-entry! (fn [& args] (reset! added args))
                  denylist/reload-snapshot (constantly nil)
                  tokens/get-user-id (constantly "admin1")]
      (is (= 200 (:status (token-denylist/token-denylist-api-routes
                           {:request-method :put
                            :uri (str "/token-denylist/tokens/" (apply str (repeat 64 "A")))
                            :request-context {:token "admin-token"}}))))
      (is (= [:token (apply str (repeat 64 "a"))] (take 2 @added)))
      (is (= :bad-request (error-type #(token-denylist/token-denylist-api-routes
                                        {:request-method :put
                                         :uri "/token-denylist/tokens/not-a-hash"
                                         :request-context {:token "admin-token"}})))))))
