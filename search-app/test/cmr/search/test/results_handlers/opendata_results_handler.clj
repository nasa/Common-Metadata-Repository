(ns cmr.search.test.results-handlers.opendata-results-handler
  "This tests the opendata-results-handler namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.results-handlers.opendata-results-handler :as opendata-results-handler]))

(deftest graphic-preview-type
  (testing "Not provided results in the field not being displayed"
    (is (nil? (opendata-results-handler/graphic-preview-type {:get-data-mime-type "Not provided"}))))
  (testing "graphic-preview-type retrieval"
    (is (= "image/gif" (opendata-results-handler/graphic-preview-type {:get-data-mime-type "image/gif"})))))

(deftest get-best-browse-image
 (let [all-browse-fields {:url "http://example.com"
                          :get-data-mime-type "image/gif"
                          :description "a test description-0"
                          :type "GET RELATED VISUALIZATION"
                          :size 0}
       only-description {:description "a test description-1"
                         :type "GET RELATED VISUALIZATION"}
       only-not-provided-mime-type {:get-data-mime-type "Not provided"
                                    :type "GET RELATED VISUALIZATION"}
       only-mime-type {:get-data-mime-type "image/gif"
                       :type "GET RELATED VISUALIZATION"}
       no-browse-fields {:size 0
                         :type "GET RELATED VISUALIZATION"
                         :get-service-mime-type "image/png"}
       not-a-browse-image {:type "GET DATA"
                           :size 0
                           :get-service-mime-type "image/png"
                           :description "a test description-3"
                           :get-data-mime-type "image/png"
                           :url "http://example.com/not-a-browse-image"}]
   (testing "Browse image related url with most fields is chosen"
     (are3 [expected-url related-urls]
       (is (= expected-url (opendata-results-handler/get-best-browse-image-related-url related-urls)))

       "Related url with only mime type. One containing Not provided, one containing the actual mime-type"
       only-mime-type [only-mime-type only-not-provided-mime-type]

       "Browse with all fields"
       all-browse-fields [only-description all-browse-fields only-mime-type]

       "Browse image with only one field chosen over a non-browse image with all the fields."
       only-mime-type [not-a-browse-image only-mime-type only-not-provided-mime-type]

       "nil is returneded for no browse images found"
       nil [not-a-browse-image]

       "empty urls"
       nil []

       "nil urls"
       nil nil))))
