(ns cmr.search.test.results-handlers.opendata-results-handler
  "This tests the opendata-results-handler namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.search.results-handlers.opendata-results-handler :as opendata-results-handler]))

(deftest graphic-preview-type
  (testing "Not provided results in the field not being displayed"
    (is (nil? (opendata-results-handler/graphic-preview-type {:format "Not provided"})))
    (is (nil? (opendata-results-handler/graphic-preview-type {:get-data-mime-type "Not provided"})))
    (is (nil? (opendata-results-handler/graphic-preview-type {:format "Not provided"
                                                              :get-data-mime-type "Not provided"}))))
  (testing "graphic-preview-type retrieval"
    (is (= "image/gif" (opendata-results-handler/graphic-preview-type {:get-data-mime-type "image/gif"})))
    (is (= "image/png" (opendata-results-handler/graphic-preview-type {:format "Not provided"
                                                                       :get-data-mime-type "image/png"})))
    (is (= "png" (opendata-results-handler/graphic-preview-type {:format "png"})))))
