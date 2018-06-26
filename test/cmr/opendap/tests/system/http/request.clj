(ns ^:system cmr.opendap.tests.system.http.request
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.http.request :as request]
    [cmr.opendap.testing.config :as test-system]))

(use-fixtures :once test-system/with-system)

(deftest default-accept
  (is (= "application/vnd.cmr-opendap.v2+json"
         (request/default-accept (test-system/system)))))

(deftest parse-accept
  (testing "just content type"
    (let [result (request/parse-accept
                  (test-system/system)
                  (request/add-accept "text/plain"))]
      (is (= "text" (:type result)))
      (is (= "plain" (:subtype result)))
      (is (= "plain" (:no-vendor-content-type result)))))
  (testing "just vendor"
    (let [result (request/parse-accept
                  (test-system/system)
                  (request/add-accept "text/vnd.nasa"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))))
  (testing "vendor & content type"
    (let [result (request/parse-accept
                  (test-system/system)
                  (request/add-accept "text/vnd.nasa+plain"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))
      (is (= "plain" (:content-type result)))))
  (testing "vendor & version"
    (let [result (request/parse-accept
                  (test-system/system)
                  (request/add-accept "text/vnd.nasa.v4"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))
      (is (= "v4" (:version result)))
      (is (= nil (:content-type result)))
      (is (= nil (:no-vendor-content-type result)))))
  (testing "vendor, version, & content type"
    (let [result (request/parse-accept
                  (test-system/system)
                  (request/add-accept "text/vnd.nasa.v4+plain"))]
      (is (= "text" (:type result)))
      (is (= "nasa" (:vendor result)))
      (is (= "v4" (:version result)))
      (is (= "plain" (:content-type result))))))

(deftest accept-api-version
  (testing "just content type"
    (is (= "v2"
           (request/accept-api-version
            (test-system/system)
            (request/add-accept "text/plain")))))
  (testing "just vendor"
    (is (= "v2"
           (request/accept-api-version
            (test-system/system)
            (request/add-accept "text/vnd.nasa")))))
  (testing "vendor & content type"
    (is (= "v2"
           (request/accept-api-version
            (test-system/system)
            (request/add-accept "text/vnd.nasa+plain")))))
  (testing "vendor & version"
    (is (= "v4"
           (request/accept-api-version
            (test-system/system)
            (request/add-accept "text/vnd.nasa.v4")))))
  (testing "vendor, version, & content type"
    (is (= "v4"
           (request/accept-api-version
            (test-system/system)
            (request/add-accept "text/vnd.nasa.v4+plain"))))))

(deftest accept-media-type
  (testing "just content type"
    (is (= "cmr-opendap.v2"
           (request/accept-media-type
            (test-system/system)
            (request/add-accept "text/plain")))))
  (testing "just vendor"
    (is (= "nasa.v2"
           (request/accept-media-type
            (test-system/system)
            (request/add-accept "text/vnd.nasa")))))
  (testing "vendor & content type"
    (is (= "nasa.v2"
           (request/accept-media-type
            (test-system/system)
            (request/add-accept "text/vnd.nasa+plain")))))
  (testing "vendor & version"
    (is (= "nasa.v4"
           (request/accept-media-type
            (test-system/system)
            (request/add-accept "text/vnd.nasa.v4")))))
  (testing "vendor, version, & content type"
    (is (= "nasa.v4"
           (request/accept-media-type
            (test-system/system)
            (request/add-accept "text/vnd.nasa.v4+plain"))))))

(deftest accept-format
  (testing "just content type"
    (is (= "plain"
           (request/accept-format
            (test-system/system)
            (request/add-accept "text/plain")))))
  (testing "just vendor"
    (is (= "json"
           (request/accept-format
            (test-system/system)
            (request/add-accept "text/vnd.nasa")))))
  (testing "vendor & content type"
    (is (= "plain"
           (request/accept-format
            (test-system/system)
            (request/add-accept "text/vnd.nasa+plain")))))
  (testing "vendor & version"
    (is (= "json"
           (request/accept-format
            (test-system/system)
            (request/add-accept "text/vnd.nasa.v4")))))
  (testing "vendor, version, & content type"
    (is (= "plain"
           (request/accept-format
            (test-system/system)
            (request/add-accept "text/vnd.nasa.v4+plain"))))))
