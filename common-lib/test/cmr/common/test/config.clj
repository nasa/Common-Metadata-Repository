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

(defconfig test-bool
  "This is a test bool configuration parameter"
  {:default true
   :type Boolean})

(defconfig test-custom-parser
  "This is a test configuration parameter with a custom parser"
  {:default {}
   :parser edn/read-string})

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
    (testing "env variable value"
      (with-env-vars
        {"CMR_TEST_STRING" "foo"}
        (is (= "foo" (test-string)))
        (testing "Overriding the value"
          (set-test-string! "bar")
          (is (= "bar" (test-string)))))))

  (testing "Long configs"
    (testing "default value"
      (is (= 5 (test-long))))
    (testing "env variable value"
      (with-env-vars
        {"CMR_TEST_LONG" "12"}
        (is (= 12 (test-long)))
        (testing "Overriding the value"
          (set-test-long! 45)
          (is (= 45 (test-long)))))))

  (testing "Double configs"
    (testing "default value"
      (is (= 0.75 (test-double))))
    (testing "env variable value"
      (with-env-vars
        {"CMR_TEST_DOUBLE" "12.2"}
        (is (= 12.2 (test-double)))
        (testing "Overriding the value"
          (set-test-double! 47.89)
          (is (= 47.89 (test-double)))))))

  (testing "Boolean configs"
    (testing "default value"
      (is (= true (test-bool))))
    (testing "env variable value"
      (with-env-vars
        {"CMR_TEST_BOOL" "false"}
        (is (= false (test-bool)))
        (testing "Overriding the value"
          (set-test-bool! true)
          (is (= true (test-bool)))))))

  (testing "Custom parser configs"
    (testing "default value"
      (is (= {} (test-custom-parser))))
    (testing "env variable value"
      (with-env-vars
        {"CMR_TEST_CUSTOM_PARSER" "[1 2 3]"}
        (is (= [1 2 3] (test-custom-parser)))
        (testing "Overriding the value"
          (set-test-custom-parser! {:a 1})
          (is (= {:a 1} (test-custom-parser))))))))

