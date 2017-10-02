(ns ^:unit cmr.client.tests.common.util
  (:require
   [clojure.test :refer :all]
   [cmr.client.common.util :as util]))

(deftest get-endpoint
  (testing "public hosts"
    (is (= "https://cmr.earthdata.nasa.gov/access-control"
           (util/get-endpoint :prod :access-control)))
    (is (= "https://cmr.uat.earthdata.nasa.gov/ingest"
           (util/get-endpoint :uat :ingest)))
    (is (= "https://cmr.sit.earthdata.nasa.gov/search"
           (util/get-endpoint :sit :search))))
  (testing "local hosts"
    (is (= "http://localhost:3011" (util/get-endpoint :local :access-control)))
    (is (= "http://localhost:3002" (util/get-endpoint :local :ingest)))
    (is (= "http://localhost:3003" (util/get-endpoint :local :search)))))

(deftest parse-endpoint
  (is "gopher://custom.host/ingest"
      (util/parse-endpoint "gopher://custom.host/ingest"))
  (is "gopher://custom.host/ingest"
      (util/parse-endpoint "gopher://custom.host/ingest" nil))
  (is "gopher://custom.host/ingest"
      (util/parse-endpoint "gopher://custom.host/ingest" "search"))
  (is "https://cmr.earthdata.nasa.gov/search"
      (util/parse-endpoint :prod :search))
  (is "https://cmr.uat.earthdata.nasa.gov/access-control"
      (util/parse-endpoint :uat :access-control))
  (is "https://cmr.sit.earthdata.nasa.gov/ingest"
      (util/parse-endpoint :sit :ingest)))
