(ns cmr.common.test.api.web-server
  "This tests capabilities of the web server component."
  (:require
   [clj-http.client :as h]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.api.web-server :as s]
   [cmr.common.lifecycle :as l]
   [cmr.common.util :as u]))

(def PORT 3123)

(def long-body
  "A body long enough that it should be compressed"
  (str/join (repeat (inc s/MIN_GZIP_SIZE) "0")))

(def short-body
  "A body short enough that it shouldn't be compressed"
  (str/join (repeat (dec s/MIN_GZIP_SIZE) "0")))

(defn routes-fn
  "The routes function to use with the web server. Returns a response long enough that it should be
  compressed."
  [system]
  (fn [request]
    (if (= (:uri request) "/short")
      {:status 200
       :headers {"Content-Type" "application/xml; charset=utf-8"}
       :body short-body}
      {:status 200
       :headers {"Content-Type" "application/xml; charset=utf-8"}
       :body long-body})))

(defn routes-fn-return-body
  "A routes function to use with the web server. Returns back what was sent"
  [system]
  (fn [request]
    {:status 200
     :body (:body request)}))

(defn get-uri
  [path accept-encoding]
  (h/with-middleware
    ;; We remove all middleware so we can get the raw input stream body back
    [h/wrap-url
     h/wrap-method]
    (h/get (str "http://localhost:" PORT path)
           {:headers {"Accept-encoding" accept-encoding}})))

(defn assert-compressed-response
  [response expected-body]
  (is (= "gzip" (get-in response [:headers "content-encoding"])))
  (is (= expected-body
         (-> response :body java.util.zip.GZIPInputStream. slurp))))

(defn assert-not-compressed-response
  [response expected-body]
  (is (not= "gzip" (get-in response [:headers "content-encoding"])))
  (is (= expected-body (-> response :body slurp))))

(deftest test-gzip-compression
  (let [server (l/start (s/create-web-server PORT routes-fn true false) nil)]
    (try
      (testing "A large body is compressed"
        (assert-compressed-response (get-uri "/long" "gzip") long-body))
      (testing "A large body is compressed with gzip, deflate"
        (assert-compressed-response (get-uri "/long" "gzip, deflate") long-body))
      (testing "A short body is not compressed"
        (assert-not-compressed-response (get-uri "/short" "gzip") short-body))
      (testing "A large body is not compressed without accept encoding header"
        (assert-not-compressed-response (get-uri "/long" nil) long-body))
      (finally
        (l/stop server nil)))))

(deftest test-max-post-size
  (let [server (l/start (s/create-web-server PORT routes-fn-return-body false false) nil)]
    (try
      (testing "post body size too large"
        (let [result (h/post (str "http://localhost:" PORT)
                             {:body (str/join (repeat (inc s/MAX_REQUEST_BODY_SIZE) "0"))
                              :throw-exceptions false})]
          (is (= 413 (:status result)))
          (is (= "Request body exceeds maximum size" (:body result)))))
      (testing "maximum post body size ok"
        (let [result (h/post (str "http://localhost:" PORT)
                             {:body (str/join (repeat s/MAX_REQUEST_BODY_SIZE "0"))
                              :throw-exceptions false})]
          (is (= 200 (:status result)))))
      (testing "post request small body"
        (let [result (h/post (str "http://localhost:" PORT)
                             {:body "test data"
                              :throw-exceptions false})]
          (is (= "test data" (:body result)))))
      (finally
        (l/stop server nil)))))

;; Code for running a performance analysis on functionality to kick back a request with
;; a body that is too large
(comment
 (require '[criterium.core :refer [with-progress-reporting bench quick-bench]])

 (def test-fn
   (#'s/wrap-request-body-size-validation (constantly nil)))

 ; Large example
 (let [lots-of-bytes (.getBytes (str/join (repeat (dec s/MAX_REQUEST_BODY_SIZE) "0")))]
   (with-progress-reporting
    (quick-bench
     (test-fn {:body (java.io.ByteArrayInputStream. lots-of-bytes)}))))


 ; Small example
 (let [small-bytes (.getBytes (str/join (repeat 10 "0")))]
   (with-progress-reporting
    (quick-bench
     (test-fn {:body (java.io.ByteArrayInputStream. small-bytes)})))))
