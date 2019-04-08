(ns cmr.common.test.api.web-server
  "This tests capabilities of the web server component."
  (:require
   [clj-http.client :as h]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.api.errors :as errors]))

(deftest test-error-masked
  (let [error-message "Token 123 does not exist"]
  (is (= "Token does not exist" (errors/mask-token-error error-message)))))
