(ns cmr.transmit.test.config
  (:require
    [clojure.test :refer :all]
    [cmr.transmit.config :as config]))

(deftest with-echo-system-token-test
  (is (= {:token "mock-echo-system-token"
          :foo "bar"}
         (config/with-echo-system-token {:foo "bar"})))
  (with-redefs [cmr.transmit.config/echo-system-token (constantly "zort")]
    (is (= {:token "zort"
            :foo "bar"}
           (config/with-echo-system-token {:foo "bar"})))))
