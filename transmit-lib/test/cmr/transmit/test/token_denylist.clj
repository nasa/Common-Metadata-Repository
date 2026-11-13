(ns cmr.transmit.test.token-denylist
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.time-keeper :as tk]
   [cmr.transmit.token-denylist :as denylist])
  (:import
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

(deftest token-hash-test
  (is (= (denylist/token-hash token) (denylist/token-hash (str "Bearer " token))))
  (testing "re-encoded JWTs hash the same"
    (is (= (denylist/token-hash "eyJhbGciOiJSUzI1NiJ9.eyJ1aWQiOiJ1MSJ9.QQ")
           (denylist/token-hash "eyJhbGciOiJSUzI1NiJ9.eyJ1aWQiOiJ1MSJ9.QR")
           (denylist/token-hash "eyJhbGciOiJSUzI1NiJ9.eyJ1aWQiOiJ1MSJ9.QQ==")))))

(deftest denied-test
  (with-fields (constantly [token-field "user:user1"])
    (let [ctx (context)]
      (is (true? (denylist/denied? ctx token (constantly "other"))))
      (is (true? (denylist/denied? ctx "other-token" (constantly "user1"))))
      (is (false? (denylist/denied? ctx "other-token" (constantly "other")))))))

(deftest redis-failure-keeps-last-loaded-test
  (tk/with-frozen-time
    (let [ctx (context)]
      (with-fields (constantly [token-field])
        (is (true? (denylist/denied? ctx token (constantly nil)))))
      (tk/advance-time! (inc (denylist/token-denylist-refresh-seconds)))
      (with-fields #(throw (Exception. "connection refused"))
        (is (true? (denylist/denied? ctx token (constantly nil))))))))
