(ns cmr.search.test.results-handlers.opendata-results-handler
  "This tests the opendata-results-handler namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.results-handlers.opendata-results-handler :as opendata-results-handler]))

(deftest distribution-is-downloadable
  (testing "Mime type distribution urls"
    (are3 [expected-mime-type related-url]
      (is (= expected-mime-type (:mediaType (opendata-results-handler/related-url->distribution related-url))))

      "downloadable url guesses correct mime type."
      "image/png" {:url "http://example.com/mime-type.png"
                   :type "GET DATA"}

      "downloadable url uses mime type when specified"
      "image/jpeg" {:url "http://example.com/mime-type.jar"
                    :get-data-mime-type "image/jpeg"
                    :type "GET DATA"}

      "downloadable url guesses mime type when get-data-mime-type is nil"
      "application/json" {:url "http://example.com/mime/type.json"
                          :get-data-mime-type "Not provided"
                          :type "GET DATA"}))

  (testing "distribution urls are as expected"
    (are3 [expected-distribution related-url]
      (is (= expected-distribution (opendata-results-handler/related-url->distribution related-url)))

      "title is defined by url-content-type, type, and sub-type"
      {:accessURL "http://example.com/"
       :title "Download this dataset through APPEEARS"}
      {:url "http://example.com/"
       :type "GET DATA"
       :sub-type "APPEEARS"
       :url-content-type "DistributionURL"}

      "sub-type is not defined so value is defaulted"
      {:accessURL "http://example.com"
       :title "View information related to this dataset"}
      {:url-content-type "PublicationURL"
       :type "VIEW RELATED INFORMATION"
       :url "http://example.com"}

      "url is downloadable without guessing mime type"
      {:downloadURL "http://example.com/mime-type"
       :mediaType "image/jpeg"}
      {:url "http://example.com/mime-type"
       :get-data-mime-type "image/jpeg"}

      "url is downloadable by guessing mime type"
      {:downloadURL "http://example.com/mime-type.jpeg"
       :mediaType "image/jpeg"}
      {:url "http://example.com/mime-type.jpeg"
       :type "GET DATA"}

      "url is downloadable by guessing mime type due to Not provided mime-type"
      {:downloadURL "http://example.com/mime-type.jpeg"
       :mediaType "image/jpeg"
       :description "test description."}
      {:url "http://example.com/mime-type.jpeg"
       :get-data-mime-type "Not provided"
       :description "test description."}

      "url is not downloadable from un-guessable mime type and mime-type specified as Not provided"
      {:accessURL "http://example.com/un-guessable-mime-type"
       :description "test description."}
      {:url "http://example.com/un-guessable-mime-type"
       :type "GET DATA"
       :description "test description."
       :get-data-mime-type "Not provided"}

      "url is not downloadable based on text/html mimetype"
      {:accessURL "http://example.com/mime-type.html"}
      {:url "http://example.com/mime-type.html"
       :get-data-mime-type "text/html"
       :type "GET RELATED VISUALIZATION"}

      "url is not downloadable from missing mime type and undeterminable mime type"
      {:accessURL "http://example.com/missing-mime-type"}
      {:url "http://example.com/missing-mime-type"
       :type "GET DATA"}

      "url is not downloadable based on mimetype being guessed as text/html and text/* not downloadable"
      {:accessURL "http://example.com/not-downloadable-html.html"}
      {:url "http://example.com/not-downloadable-html.html"}

      "url is downloadable based on mimetype being guessed for whitelisted mime type"
      {:downloadURL "http://example.com/csv.csv"
       :mediaType "text/csv"}
      {:url "http://example.com/csv.csv"})))

(deftest graphic-preview-type
  (testing "Not provided results in the field not being displayed"
    (is (nil? (opendata-results-handler/graphic-preview-type {:get-data-mime-type "Not provided"}))))
  (testing "graphic-preview-type retrieval"
    (is (= "image/gif" (opendata-results-handler/graphic-preview-type {:get-data-mime-type "image/gif"})))))

(deftest ngda-keywords
  (let [keywords ["Science Keyword"]]
    (testing "Check that the NGDA tag is not added for records that make no mention of instruments"
      (is (= ["Science Keyword"]
             (opendata-results-handler/keywords keywords {}))))
    (testing "Check that the NGDA tags are not added to unrelated instruments"
      (is (= ["Science Keyword"]
             (opendata-results-handler/keywords keywords {:instruments [{:short-name "unrelated"}]}))))
    (testing "Check that NGDA tags _are_ added for records that use MODIS"
      (is (= ["Science Keyword" "NGDA" "National Geospatial Data Asset"]
             (opendata-results-handler/keywords keywords {:instruments [{:short-name "MODIS"}]}))))
    (testing "Check that NGDA tags _are_ added for records that use ASTER"
      (is (= ["Science Keyword" "NGDA" "National Geospatial Data Asset"]
             (opendata-results-handler/keywords keywords {:instruments [{:short-name "ASTER"}]}))))
    (testing "Check that NGDA tags _are_ added for records that use both MODIS and ASTER"
      (is (= ["Science Keyword" "NGDA" "National Geospatial Data Asset"]
             (opendata-results-handler/keywords keywords {:instruments [{:short-name "MODIS"} {:short-name "ASTER"}]}))))))

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


(deftest config-email
  (let [personnel->contact-email 
        (opendata-results-handler/personnel->contact-email nil)]
    (is (= "cmr-support@earthdata.nasa.gov" personnel->contact-email))))