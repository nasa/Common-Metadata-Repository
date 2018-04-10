(ns ^:system cmr.opendap.tests.system.rest.app
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.http.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc])
  (:import
    (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(deftest admin-routes
  (testing "ping routes ..."
    (let [response @(httpc/get (format "http://localhost:%s/ping"
                                       (test-system/http-port))
                               (request/add-token-header
                                {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:result "pong"}
             (util/parse-response response))))
    (let [response @(httpc/post (format "http://localhost:%s/ping"
                                        (test-system/http-port))
                                (request/add-token-header
                                 {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:result "pong"}
             (util/parse-response response))))))

(deftest ous-collection-get-with-collection-params
  (testing "Minimal GET ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables []
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "GET one variable ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?variables=V1200241812-EDF_OPS")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables ["V1200241812-EDF_OPS"]
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "GET with variables ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?variables=V1200241812-EDF_OPS,V1200241813-EDF_OPS")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables ["V1200241812-EDF_OPS" "V1200241813-EDF_OPS"]
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "GET with granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]
              :exclude-granules nil
              :variables []
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "GET without granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
                                  "&exclude-granules=true")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]
              :exclude-granules "true"
              :variables []
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "GET with subset ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?subset=lat(56.109375,67.640625)"
                                  "&subset=lon(-9.984375,19.828125)")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables []
              :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "GET with bounding box ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?bounding-box="
                                  "-9.984375,56.109375,19.828125,67.640625")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables []
              :subset nil
              :bounding-box "-9.984375,56.109375,19.828125,67.640625"}
             (util/parse-response response))))))

(deftest ous-collection-post-with-collection-params
  (testing "Minimal POST ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables []
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "POST with one variable ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:variables ["V1200241812-EDF_OPS"]})
                       (request/add-token-header {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables ["V1200241812-EDF_OPS"]
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "POST with variables ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:variables ["V1200241812-EDF_OPS"
                                    "V1200241813-EDF_OPS"]})
                       (request/add-token-header {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables ["V1200241812-EDF_OPS" "V1200241813-EDF_OPS"]
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "POST with granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200187775-EDF_OPS"
                                   "G1200245955-EDF_OPS"]})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]
              :exclude-granules nil
              :variables []
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "POST without granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200187775-EDF_OPS"
                                   "G1200245955-EDF_OPS"]
                        :exclude-granules "true"})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]
              :exclude-granules "true"
              :variables []
              :subset nil
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "POST with subset ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:subset ["lat(56.109375,67.640625)"
                                 "lon(-9.984375,19.828125)"]})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables []
              :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]
              :bounding-box nil}
             (util/parse-response response)))))
  (testing "POST with bounding box ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:bounding-box "-9.984375,56.109375,19.828125,67.640625"})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= {:collection-id "C1200187767-EDF_OPS"
              :format nil
              :granules []
              :exclude-granules nil
              :variables []
              :subset nil
              :bounding-box "-9.984375,56.109375,19.828125,67.640625"}
             (util/parse-response response))))))
