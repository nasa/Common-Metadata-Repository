(ns cmr.common.test.config
  (:require [clojure.test :refer :all]
            [cmr.common.config :as c :refer [defconfig]]
            [clojure.edn :as edn]))

(defn redefined-override-config-values-fixture
  "Replaces the override config values that are used when testing to avoid the test conflicting with
  other things in the system that may be modifying the config values."
  [f]
  (with-redefs [c/runtime-config-values (atom {})]
    (f)))

(use-fixtures :each redefined-override-config-values-fixture)

(defconfig test-string
  "This is a test string configuration parameter"
  {:default "abc"
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

(defconfig test-default-fn-call
  "This is a test config that calls functions for setting the default and the type."
  {:default (+ 1 2)
   :type Long})

(defmacro with-env-vars
  "Overrides the environment variables the config values will see within the block. Accepts a map
  of environment variables to values."
  [env-var-values & body]
  `(with-redefs [c/env-var-value ~env-var-values]
     ~@body))


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
          (is (= "foo" (test-string)))))))

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

  (testing "default using a function call"
    (is (= 3 (test-default-fn-call)))))

(deftest test-maybe-long
  (is (= 99 (c/maybe-long "99")))
  (is (nil? (c/maybe-long nil))))
