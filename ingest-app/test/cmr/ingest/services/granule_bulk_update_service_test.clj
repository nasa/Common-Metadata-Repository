(ns cmr.ingest.services.granule-bulk-update-service-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.ingest.services.granule-bulk-update.utils.umm-g :as umm-g-util]
   [cmr.ingest.services.granule-bulk-update-service :as service]))

(deftest granule-bulk-update-chunk-size-test
  (is (and (int? (service/granule-bulk-update-chunk-size))
           (pos? (service/granule-bulk-update-chunk-size)))))

(def concept {:revision-id 1
              :deleted false
              :format "application/vnd.nasa.cmr.umm+json;version=1.6.4"
              :provider-id "PROV1"
              :transaction-id 13
              :native-id "G2206822672-CDDIS.json"
              :concept-id "G1200000002-PROV1"
              :created-at "2023-02-22T00:46:27.737Z"
              :metadata "{\"GranuleUR\" : \"gnss_data_highrate_2022_023_22m_20_ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz.json\",
                         \"RelatedUrls\" : [{\"URL\" : \"https://cddis.nasa.gov/archive/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz\",
                                             \"Type\" : \"GET DATA\"},
                                            {\"URL\" : \"ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz\",
                                             \"Type\" : \"GET DATA\"}]}"
              :revision-date "2023-02-22T13:47:00.271Z"
              :extra-fields {:parent-collection-id "C1200000001-PROV1"
                             :delete-time nil
                             :granule-ur "gnss_data_highrate_2022_023_22m_20_ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz.json"}
              :concept-type :granule})

(deftest update-umm-g-metadata-test
  (let [context {}
        user-id "George"]

    (testing "Testing update of a RelatedURL"
      (let [update-values [{:from "https://cddis.nasa.gov/archive/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz"
                            :to "http://Test.LINK.com"}
                           {:from "ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz"
                            :to "ftp://Test.LINK2.com"}]
            actual (#'service/update-umm-g-metadata context concept update-values user-id umm-g-util/update-urls)
            parsed-metadata (json/decode (:metadata actual) true)
            urls (map :URL (:RelatedUrls parsed-metadata))]
        (is (some #(= "http://Test.LINK.com" %) urls))
        (is (some #(= "ftp://Test.LINK2.com" %) urls))
        (is (nil? (some #(= "https://cddis.nasa.gov/archive/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz" %) urls)))
        (is (nil? (some #(= "ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz" %) urls)))
        (is (= "George" (:user-id actual)))
        (is (= 2 (:revision-id actual)))))

    (testing "Testing not updating a non existing RelatedURL"
      (let [update-values [{:from "https://cddis.nasa.gov/archive1"
                            :to "http://Test.LINK.com"}]
            actual (#'service/update-umm-g-metadata context concept update-values user-id umm-g-util/update-urls)
            parsed-metadata (json/decode (:metadata actual) true)
            urls (map :URL (:RelatedUrls parsed-metadata))]
        (is (nil? (some #(= "http://Test.LINK.com" %) urls)))))

    (testing "Testing adding RelatedURL when URLs exist."
      (let [update-values [{:URL "http://Test.LINK3.com"
                            :MimeType "image/jpeg"
                            :Format "JPEG"
                            :Type "GET DATA"
                            :Description "Some dummy URL."
                            :SizeUnit "TB"
                            :Size 1}]
            actual (#'service/update-umm-g-metadata context concept update-values user-id umm-g-util/append-urls)
            parsed-metadata (json/decode (:metadata actual) true)
            urls (map :URL (:RelatedUrls parsed-metadata))]
        (is (some #(= "http://Test.LINK3.com" %) urls))))

    (testing "Testing Removing RelatedURL."
      (let [update-values [{:URL "https://cddis.nasa.gov/archive/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz"}]
            actual (#'service/update-umm-g-metadata context concept update-values user-id umm-g-util/remove-urls)
            parsed-metadata (json/decode (:metadata actual) true)
            urls (map :URL (:RelatedUrls parsed-metadata))]
        (is (nil? (some #(= "https://cddis.nasa.gov/archive/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz" %) urls)))))

    (testing "Testing Removing all RelatedURLs."
      (let [update-values [{:URL "https://cddis.nasa.gov/archive/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz"}
                           {:URL "ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz"}]
            actual (#'service/update-umm-g-metadata context concept update-values user-id umm-g-util/remove-urls)
            parsed-metadata (json/decode (:metadata actual) true)]
        (is (nil? (seq (:RelatedUrls parsed-metadata))))))

    (testing "Testing Adding a RelatedURL when non exist."
      (let [update-values [{:URL "https://cddis.nasa.gov/archive/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz"}
                           {:URL "ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2022/023/22m/20/ENAO00PRT_R_20220232000_15M_05M_MM.rnx.gz"}]
            update-values-add [{:URL "http://Test.LINK3.com"
                                :Type "GET DATA"}]
            removed-url-concept (#'service/update-umm-g-metadata context concept update-values user-id umm-g-util/remove-urls)
            added-url-concept (#'service/update-umm-g-metadata context removed-url-concept update-values-add user-id umm-g-util/append-urls)
            parsed-metadata (json/decode (:metadata added-url-concept) true)
            urls (map :URL (:RelatedUrls parsed-metadata))]
        (is (is (some #(= "http://Test.LINK3.com" %) urls)))))))
