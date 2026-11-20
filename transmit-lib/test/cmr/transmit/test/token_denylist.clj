(ns cmr.transmit.test.token-denylist
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.transmit.config]
   [cmr.transmit.token-denylist :as denylist])
  (:import
   (java.util Base64)
   (org.apache.commons.codec.digest DigestUtils)))

(use-fixtures :each (fn [f]
                      (reset! @#'denylist/last-loaded nil)
                      (f)))

;; Protocol functions are called directly on records, so the store is replaced instead.
(defmacro ^:private with-fields
  [fields-fn & body]
  `(with-redefs [cmr.transmit.token-denylist/store
                 (constantly (reify hash-cache/CmrHashCache
                               (get-keys [_# _#] (~fields-fn))))]
     ~@body))

(defn- context
  []
  {:system {:caches {denylist/snapshot-cache-key (denylist/create-snapshot-cache)}}})

(def ^:private token "opaque-launchpad-style-token-value")

(def ^:private token-field (str "token:" (DigestUtils/sha256Hex token)))

(def ^:private edl-header
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder))
                   (.getBytes "{\"typ\":\"JWT\",\"origin\":\"Earthdata Login\",\"alg\":\"RS256\"}")))

;; Sample token, safe for use in tests: a made-up header and the payload {"uid":"u1"}, never signed.
(def ^:private signed (str edl-header ".eyJ1aWQiOiJ1MSJ9"))

(deftest token-hash-test
  (is (= (DigestUtils/sha256Hex token) (denylist/token-hash token) (denylist/token-hash (str "Bearer " token))))
  (testing "an EDL JWT hashes its signed header and payload, so signature variants can't escape"
    (is (= (DigestUtils/sha256Hex signed)
           (denylist/token-hash (str signed ".QQ"))
           (denylist/token-hash (str signed ".QR"))
           (denylist/token-hash (str signed ".AABB"))
           (denylist/token-hash (str "Bearer " signed ".QQ"))
           (denylist/token-hash (str signed ".QQBearer-"))
           (denylist/token-hash (str signed ".QQBearer "))
           (denylist/token-hash (str signed ".QQBearer\n"))
           (denylist/token-hash (str signed ".!Bearer.Bearer.Bearer-QQ")))))
  (testing "the system token is never logged"
    (is (nil? (denylist/token-hash-for-log (cmr.transmit.config/echo-system-token))))))

(deftest denied-test
  (with-fields (constantly [token-field "user:User1"])
    (let [ctx (context)]
      (is (true? (denylist/denied? ctx token (constantly "other"))))
      (is (true? (denylist/denied? ctx "other-token" (constantly "USER1"))))
      (is (false? (denylist/denied? ctx "other-token" (constantly "other"))))
      (is (true? (denylist/user-denied? ctx "USER1")))
      (testing "a failed user lookup fails the request"
        (is (thrown? Exception (denylist/denied? ctx "other-token" #(throw (Exception. "EDL down")))))))))

(deftest redis-failure-keeps-last-loaded-test
  (let [clock (atom 0)]
    (with-redefs [cmr.transmit.token-denylist/now-ms #(deref clock)]
      (let [ctx (context)]
        (with-fields (constantly [token-field])
          (is (true? (denylist/denied? ctx token (constantly nil)))))
        (swap! clock + (* 1000 (inc (denylist/token-denylist-refresh-seconds))))
        (with-fields #(throw (Exception. "connection refused"))
          (is (true? (denylist/denied? ctx token (constantly nil)))))))))
