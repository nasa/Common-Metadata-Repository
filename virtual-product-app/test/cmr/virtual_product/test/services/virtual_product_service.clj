(ns cmr.virtual-product.test.services.virtual-product-service
  (:require [clojure.test :refer :all]
            [cmr.virtual-product.services.virtual-product-service :as vps]))


(deftest responses-not-causing-error-tests
  (are [f status]
       (nil? (f {:status status :body "body"} "granule-ur"))
       #'vps/handle-update-response 200
       #'vps/handle-update-response 201
       #'vps/handle-update-response 409
       #'vps/handle-delete-response 204
       #'vps/handle-delete-response 409))

(defn- assert-error
  [f status body granule-ur expected]
  (try
    (f {:status status :body body} granule-ur)
    (catch Exception e
      (is (= expected (.getMessage e))))))

(deftest responses-causing-error-tests
  (testing "Testing unexpected status code in an update response"
    (assert-error #'vps/handle-update-response 500 "body" "granule-ur"
                  (str "Received unexpected status code [500] and the following response when "
                       "ingesting the virtual granule [granule-ur] : [{:status 500, :body \"body\"}]")))
  (testing "Testing status code 404 in a  delete response"
    (assert-error #'vps/handle-delete-response 404 "body" "granule-ur"
                  (str "Received a response with status code [404] and the following response body "
                       "when deleting the virtual granule [granule-ur] : [\"body\"]."
                       " The delete request will be retried.")))
  (testing "Testing unexpected status code in a delete response"
    (assert-error #'vps/handle-delete-response 500 "body" "granule-ur"
                  (str "Received unexpected status code [500] and the following response when "
                       "deleting the virtual granule [granule-ur] : [{:status 500, :body \"body\"}]"))))


