(ns cmr.common.test.config
  (:require 
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [cmr.common.config :as c :refer [defconfig]]
   [cmr.common.test.test-util :refer [with-env-vars]]))

(defn redefined-override-config-values-fixture
  "Replaces the override config values that are used when testing to avoid the test conflicting with
  other things in the system that may be modifying the config values."
  [f]
  (with-bindings {#'c/runtime-config-values (atom {})}
    (f)))

(use-fixtures :each redefined-override-config-values-fixture)

(defconfig test-string
  "This is a test string configuration parameter"
  {:default "abc"
   :type String})

(defconfig test-string-with-nil
  "This is a test string configuration parameter with a nil default"
  {:default nil
   :type String})

(defconfig test-long
  "This is a test long configuration parameter"
  {:default 5
   :type Long})

(defconfig test-double
  "This is a test double configuration parameter"
  {:default 0.75
   :type Double})

(defconfig test-bool-true
  "This is a test bool configuration parameter defaulting to true"
  {:default true
   :type Boolean})

(defconfig test-bool-false
  "This is a test bool configuration parameter defaulting to false"
  {:default false
   :type Boolean})

(defconfig test-custom-parser
  "This is a test configuration parameter with a custom parser"
  {:default {}
   :parser edn/read-string})

(defconfig test-opensearch-consortiums
  "Includes all the consortiums that opensearch contains."
  {:default ["CWIC" "FEDEO" "GEOSS" "CEOS" "EOSDIS"]
   :parser #(json/decode ^String %)})

(defconfig test-edn
  "This is a test bool configuration parameter defaulting to false"
  {:default {}
   :type :edn})

(defconfig test-default-fn-call
  "This is a test config that calls functions for setting the default and the type."
  {:default (+ 1 2)
   :type Long})

(deftest def-config-test
  (testing "String configs"
    (testing "default value"
      (is (= "abc" (test-string))))
    (testing "Overriding the value"
      (set-test-string! "bar")
      (is (= "bar" (test-string)))
      (testing "env variable value"
        (with-env-vars
          {"CMR_TEST_STRING" "foo"}
          (is (= "foo" (test-string))))))
    (testing "String configs with default nil"
      (testing "default value"
        (is (nil? (test-string-with-nil))))
      (testing "override"
        (set-test-string-with-nil! "quux")
        (is (= "quux" (test-string-with-nil))))))

  (testing "Long configs"
    (testing "default value"
      (is (= 5 (test-long))))

    (testing "Overriding the value"
      (set-test-long! 45)
      (is (= 45 (test-long)))
      (testing "env variable value"
        (with-env-vars
          {"CMR_TEST_LONG" "12"}
          (is (= 12 (test-long)))))))

  (testing "Double configs"
    (testing "default value"
      (is (= 0.75 (test-double))))

    (testing "Overriding the value"
      (set-test-double! 47.89)
      (is (= 47.89 (test-double)))
      (testing "env variable value"
        (with-env-vars
          {"CMR_TEST_DOUBLE" "12.2"}
          (is (= 12.2 (test-double)))))))

  (testing "Boolean configs"
    (testing "default value true"
      (is (= true (test-bool-true))))
    (testing "default value false"
      (is (= false (test-bool-false))))

    (testing "Overriding the value"
      (set-test-bool-true! true)
      (is (= true (test-bool-true)))
      (testing "env variable value"
        (with-env-vars
          {"CMR_TEST_BOOL_TRUE" "false"}
          (is (= false (test-bool-true)))))))

  (testing "Custom parser configs"
    (testing "default value"
      (is (= {} (test-custom-parser))))

    (testing "Overriding the value"
      (set-test-custom-parser! {:a 1})
      (is (= {:a 1} (test-custom-parser)))
      (testing "env variable value"
        (with-env-vars
          {"CMR_TEST_CUSTOM_PARSER" "[1 2 3]"}
          (is (= [1 2 3] (test-custom-parser)))))))

  (testing "Custom parser opensearch-consortiums"
    (testing "default value"
      (is (= ["CWIC" "FEDEO" "GEOSS" "CEOS" "EOSDIS"] (test-opensearch-consortiums))))

    (testing "Overriding the value"
      (set-test-opensearch-consortiums! ["CWIC" "FEDEO"])
      (is (= 2 (count (test-opensearch-consortiums))))
      (is (= ["CWIC" "FEDEO"] (test-opensearch-consortiums)))
      (testing "env variable value"
        (with-env-vars
          ;; Note: comma are needed for the parser to work
          {"CMR_TEST_OPENSEARCH_CONSORTIUMS" "[\"CWIC\", \"FEDEO\", \"GEOSS\"]"}
          (is (= 3 (count (test-opensearch-consortiums))))
          (is (= ["CWIC" "FEDEO" "GEOSS"] (test-opensearch-consortiums)))))))

  (testing "EDN configs"
    (testing "default value"
      (is (= {} (test-edn))))

    (testing "Overriding the value"
      (set-test-edn! {:a 1})
      (is (= {:a 1} (test-edn)))
      (testing "env variable value"
        (with-env-vars
          {"CMR_TEST_EDN" "{\"key1\" [\"value1\",\"value2\"]
                            \"key2\" [\"value3\"]}"}
          (is (= {"key1" ["value1" "value2"]
                  "key2" ["value3"]}  (test-edn)))))))

  (testing "default using a function call"
    (is (= 3 (test-default-fn-call)))))

(deftest test-maybe-long
  (is (= 99 (c/maybe-long "99")))
  (is (nil? (c/maybe-long nil))))

;;define two sample configs to be used by test-check-env-vars
(defconfig test-health-check-timeout-seconds
  "Timeout in seconds for health check operation."
  {:default 10 :type Long})

(defconfig test-default-job-start-delay
  "The start delay of the job in seconds."
  {:default 5
   :type Long})

(deftest test-check-env-vars
  (is (false? (c/check-env-vars {:cmr-test-default-job-start-delay "common-lib test defconfig",
                                 :cmr-test-health-check-timeout-seconds "common-lib test defconfig"})))
  (is (true? (c/check-env-vars {:cmr-not-recognizable "not recognized"}))))
