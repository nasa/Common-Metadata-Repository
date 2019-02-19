(ns cmr.virtual-product.test.services.virtual-product-service
  (:require
   [clojure.test :refer :all]
   [cmr.message-queue.config :as queue-config]
   [cmr.virtual-product.services.virtual-product-service :as vps]))

(deftest responses-not-causing-error-tests
  (testing "Update responses not causing error"
    (are [status]
         (nil? (#'vps/handle-update-response {:status status :body "body"} "granule-ur"))
         200 201 409 204 409))
  (testing "Delete responses not causing error"
    (are [status retry-count]
         (nil? (#'vps/handle-delete-response {:status status :body "body"} "granule-ur" retry-count))
         204 1
         409 1
         404 (inc (count (queue-config/time-to-live-s))))))

; status body granule-ur
(defn- assert-error
  [f expected-error]
  (try (f)
    (catch Exception e
      (is (= expected-error (.getMessage e))))))

(deftest responses-causing-error-tests
  (testing "Testing unexpected status code in an update response"
    (assert-error (partial #'vps/handle-update-response
                   {:status 500 :body "body"} "granule-ur")
                  (str "Received unexpected status code [500] and the following response when "
                       "ingesting the virtual granule [granule-ur] : [{:status 500, :body \"body\"}]")))
  (testing "Testing status code 404 in a  delete response"
    (assert-error (partial #'vps/handle-delete-response {:status 404 :body "body"} "granule-ur"
                           (dec (count (queue-config/time-to-live-s))))
                  (str "Received a response with status code [404] and the following response body "
                       "when deleting the virtual granule [granule-ur] : [\"body\"]."
                       " The delete request will be retried.")))
  (testing "Testing unexpected status code in a delete response"
    (assert-error (partial #'vps/handle-delete-response {:status 500 :body "body"} "granule-ur" 1)
                  (str "Received unexpected status code [500] and the following response when "
                       "deleting the virtual granule [granule-ur] : [{:status 500, :body \"body\"}]"))))
