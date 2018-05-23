(ns cmr.opendap.tests.unit.http.request
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.http.request :as request]))

(deftest parse-accept
  (testing "just content type"
    (let [result (request/parse-accept (request/add-accept "text/plain"))]
      (is (= "text" (:type result)))
      (is (= "plain" (:subtype result)))
      (is (= "plain" (:no-vendor-content-type result)))))
  (testing "just vendor"
    (let [result (request/parse-accept (request/add-accept "text/vnd.nasa"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))))
  (testing "vendor & content type"
    (let [result (request/parse-accept
                  (request/add-accept "text/vnd.nasa+plain"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))
      (is (= "plain" (:content-type result)))))
  (testing "vendor & version"
    (let [result (request/parse-accept
                  (request/add-accept "text/vnd.nasa.v4"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))
      (is (= "v4" (:version result)))
      (is (= nil (:content-type result)))
      (is (= nil (:no-vendor-content-type result)))))
  (testing "vendor, version, & content type"
    (let [result (request/parse-accept
                  (request/add-accept "text/vnd.nasa.v4+plain"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))
      (is (= "v4" (:version result)))
      (is (= "plain" (:content-type result))))))
