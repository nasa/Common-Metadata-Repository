(ns cmr.common.test.test-util
  (:require [clojure.test :refer [is]]
            [clojure.string :as str])
  (:import clojure.lang.ExceptionInfo))

(defn assert-exception-info-contains-errors
  "Asserts the ExceptionInfo exception contains a "
  [e error-type errors]
  (is (= {:type error-type :errors errors} (ex-data e))))

(defmacro assert-exception-thrown-with-errors
  [error-type errors & body]
  `(try
     (do
       ~@body)
     (is false "No exception was thrown")
     (catch ExceptionInfo e#
       (assert-exception-info-contains-errors e# ~error-type ~errors))))

(defn message->regex
  "Converts an expected message into the a regular expression that matches the exact string.
  Handles escaping special regex characters"
  [msg]
  (-> msg
      (str/replace #"\[" "\\\\[")
      (str/replace #"\]" "\\\\]")
      re-pattern))