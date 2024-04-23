(ns cmr.common-app.test.api.request-logger
  "Unit tests for the functions supporting the request-logger"
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common-app.api.request-logger :as req-logger]
   [cmr.common.util :as util :refer [are3]]))

(defn basic-request
  [options]
  (-> {:query-params {}
       :scheme "http"
       :server-name "localhost"
       :server-port "3003"
       :uri "/collections"}
      (merge options)))

(deftest request-to-uri-tests

  (testing "basic check"
    (let [request->uri #'cmr.common-app.api.request-logger/request->uri
          _request {:query-params {} :schemes "" :server-names "" :server-port "" :uri ""}
          request {}
          expected "null://null:nullnull"
          actual (request->uri request)]
      (is (not-empty actual))
      (is (= expected actual))))

  (testing "check configurable options in request->uri"
    (let [request->uri #'cmr.common-app.api.request-logger/request->uri]
      (are3
       [expected options]
       (is (= expected (request->uri (basic-request options))))

       "no options"
       "http://localhost:3003/collections"
       {}

       "secure scheme"
       "https://localhost:3003/collections"
       {:scheme "https"}

       "server name"
       "http://example.gov:3003/collections"
       {:server-name "example.gov"}

       "server port"
       "http://localhost:1234/collections"
       {:server-port 1234}

       "URI path"
       "http://localhost:3003/granules"
       {:uri "/granules"}

       "with queries"
       "http://localhost:3003/collections?keyword=fish"
       {:query-params {"keyword" "fish"}}))))

(deftest scrub-token-from-map-tests

  (testing "basic check…"
    (let [scrub-token-from-map #'cmr.common-app.api.request-logger/scrub-token-from-map]
      (are3
       [expected options]
       ;; Take any number of 'options' and expand them out to cover both the
       ;; single and double arity calls
       (is (= expected (apply scrub-token-from-map options)))

       "no map"
       nil
       [nil]

       "empty map"
       {}
       [{}]

       "ignore non-tokens"
       {:server-name "example.gov"}
       [{:server-name "example.gov"}]

       "short token"
       {:token "XXX"}
       [{:token "what"}]

       "long token"
       {:token "chop XXXiddle"}
       [{:token "chop out the middle"}]

       "named field"
       {"token" "some XXX"}
       [{"token" "some value"} "token"]

       "don't change both, single arity"
       {"token" "keep this" :token "XXX"}
       [{"token" "keep this" :token "die"}]

       "don't change both, double arity"
       {"token" "keep this" :token "XXX"}
       [{"token" "keep this" :token "die"} :token]))))


(deftest dump-param-test
  (testing "dump param tests"
    (let [dump-param #'cmr.common-app.api.request-logger/dump-param]
      (are3
       [expected given]
       (is (= expected (apply dump-param given)))

       "nothing should be changed"
       {:left :0}
       [{:a {:left :0}} :a]

       "scrub token key as :token"
       {:left :0 :right :1 :token "bad-XXX"}
       [{:a {:left :0 :right :1 :token "bad-value" }} :a]

       "scrub token key as string token"
       {:left :0 :right :1 "token" "Bad-XXX"}
       [{:a {:left :0 :right :1 "token" "Bad-value"}} :a]

       "scrub both kinds of tokens"
       {:left :0 :right :1 :token "bad-XXX" "token" "Bad-XXX"}
       [{:a {:left :0 :right :1 :token "bad-value" "token" "Bad-value"}} :a]

       "to many items, return fewer"
       {"a" 1 "b" 2 "c" 3 "d" 4 "e" 5}
       [{:query-param {"a" 1 "b" 2 "c" 3 "d" 4 "e" 5 "f" 6 "g" 7}} :query-param 5]

       "Real world example with only 7 items with scrubbed tokens returned"
       ;; expected
       {"a" 1 "b" 2 #_"c" #_3 "d" 4 "e" 5 "f" 6 #_"g" #_7
        :token "bad-XXX"
        "token" "Bad-XXX"}
       ;; request map, field in request to clean, number to limit by
       [{:query-param {"a" 1 "b" 2 "c" 3 "d" 4 "e" 5 "f" 6 "g" 7
                       :token "bad-value"
                       "token" "Bad-Value"}}
        :query-param
        7]))))
