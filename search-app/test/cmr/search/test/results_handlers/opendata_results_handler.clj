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

(deftest distribution-test
  (let [make-distributions #'opendata-results-handler/make-distributions]
    (are3 [expected related-urls doi]
      (is (= (set expected)
             (set (make-distributions related-urls doi))))

      "Related URLs present with doi in doi: format"
      [{:accessURL "https://foo.bar/baz"
        :description "test description"}
       {:accessURL "https://scholar.google.com/scholar?q=10.0000%2FTest%2FTEST%2FDATA301"
        :title "Google Scholar search results",
        :description "Search results for publications that cite this dataset by its DOI."}]
      [{:description "test description"
        :url "https://foo.bar/baz"}]
      "doi:10.0000/Test/TEST/DATA301"

      "Related URLs present with doi not in doi: format"
      [{:accessURL "https://foo.bar/baz"
        :description "test description"}
       {:accessURL "https://scholar.google.com/scholar?q=This+is+a+test+string"
        :title "Google Scholar search results",
        :description "Search results for publications that cite this dataset by its DOI."}]
      [{:description "test description"
        :url "https://foo.bar/baz"}]
      "This is a test string"

      "Related URLs present with doi nil"
      [{:accessURL "https://foo.bar/baz"
        :description "test description"}]
      [{:description "test description"
        :url "https://foo.bar/baz"}]
      nil

      "Related URLs nil with doi in doi: format"
      [{:accessURL "https://scholar.google.com/scholar?q=10.0000%2FTest%2FTEST%2FDATA301"
        :title "Google Scholar search results",
        :description "Search results for publications that cite this dataset by its DOI."}]
      nil
      "doi:10.0000/Test/TEST/DATA301"

      "Related URLs nil with doi not in doi: format"
      [{:accessURL "https://scholar.google.com/scholar?q=This+is+a+test+string"
        :title "Google Scholar search results",
        :description "Search results for publications that cite this dataset by its DOI."}]
      nil
      "This is a test string"

      "Related URLs nil with doi nil"
      nil
      nil
      nil)))
