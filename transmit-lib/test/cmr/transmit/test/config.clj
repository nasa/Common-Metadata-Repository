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

(deftest format-public-root-url
  (testing "full URL"
    (is (= "scheme://host.com:9999/relative/root/url/"
           (config/format-public-root-url {:protocol "scheme"
                                           :host "host.com"
                                           :port 9999
                                           :relative-root-url "/relative/root/url"}))))
  (testing "URL empty relative URL"
    (is (= "scheme://host.com:9999/"
           (config/format-public-root-url {:protocol "scheme"
                                           :host "host.com"
                                           :port 9999
                                           :relative-root-url ""}))))
  (testing "URL no relative URL"
    (is (= "scheme://host.com:9999/"
           (config/format-public-root-url {:protocol "scheme"
                                           :host "host.com"
                                           :port 9999}))))
  (testing "URL with no port"
    (is (= "scheme://host.com/relative/root/url/"
           (config/format-public-root-url {:protocol "scheme"
                                           :host "host.com"
                                           :relative-root-url "/relative/root/url"}))))
  (testing "URL with no port and empty relative URL"
    (is (= "scheme://host.com/"
           (config/format-public-root-url {:protocol "scheme"
                                           :host "host.com"
                                           :relative-root-url ""}))))
  (testing "URL with no port and no relative URL"
    (is (= "scheme://host.com/"
           (config/format-public-root-url {:protocol "scheme"
                                           :host "host.com"})))))

(deftest application-public-root-url
  (is (= "http://localhost:3011/"
         (config/application-public-root-url :access-control)))
  (is (= "http://localhost:3006/"
         (config/application-public-root-url :bootstrap)))
  (is (= "http://localhost:3004/"
         (config/application-public-root-url :indexer)))
  (is (= "http://localhost:3002/"
         (config/application-public-root-url :ingest)))
  (is (= "http://localhost:2999/"
         (config/application-public-root-url :kms)))
  (is (= "http://localhost:3001/"
         (config/application-public-root-url :metadata-db)))
  (is (= "http://localhost:3003/"
         (config/application-public-root-url :search)))
  (is (= "http://localhost:3008/"
         (config/application-public-root-url :urs)))
  (is (= "http://localhost:3009/"
         (config/application-public-root-url :virtual-product))))
