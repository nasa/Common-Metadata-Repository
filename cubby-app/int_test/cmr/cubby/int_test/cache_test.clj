(ns cmr.cubby.int-test.cache-test
  "Provides integration tests for the cubby application"
  (:require [clojure.test :refer :all]
            [cmr.transmit.cubby :as c]
            [cmr.cubby.int-test.utils :as u]))

;; TODO refactor the run-app fixture to be once per jvm session somehow.
(use-fixtures :once u/run-app-fixture)
(use-fixtures :each u/reset-fixture)


(comment

  (c/set-value (u/conn-context) "foo" "the value" true)


  (c/get-value (u/conn-context) "charlie" true)
  (c/get-value (u/conn-context) "charlie")
  (c/get-value (u/conn-context) "foo")

  )

(deftest initial-state
  (testing "retrieve unsaved key returns 404"
    (is (= 404 (:status (c/get-value (u/conn-context) "foo" true)))))
  (testing "no keys found"
    (is (= [] (c/get-keys (u/conn-context))))))

(deftest set-and-retrieve-value-basic-test
  (testing "save value the first time"
    (u/assert-value-saved-and-retrieved "foo" "the value")
    (testing "keys are available"
      (u/assert-keys ["foo"])))

  (testing "replace value"
    (u/assert-value-saved-and-retrieved "foo" "updated value")))

(deftest save-multiple-keys
  (u/assert-value-saved-and-retrieved "foo" "foo value")
  (u/assert-value-saved-and-retrieved "bar" "bar value")
  (u/assert-value-saved-and-retrieved "charlie" "charlie value")
  (u/assert-keys ["foo" "bar" "charlie"]))

(deftest delete-key
  (u/assert-value-saved-and-retrieved "foo" "foo value")
  (u/assert-value-saved-and-retrieved "bar" "bar value")
  (u/assert-value-saved-and-retrieved "charlie" "charlie value")

  (is (= 200 (:status (c/delete-value (u/conn-context) "bar" true))))

  (testing "404 is returned after deleting"
    (is (= 404 (:status (c/get-value (u/conn-context) "bar" true)))))

  (testing "key is removed after deleting"
    (u/assert-keys ["foo" "charlie"])))

(deftest url-encode-keys
  (let [test-chars "`~!@#$%^& *()_+-={}[]\\|;':\",./<>?"
        key-name (str "key" test-chars)
        value (str "value" test-chars)]
    (u/assert-value-saved-and-retrieved key-name value)
    (u/assert-keys [key-name])))