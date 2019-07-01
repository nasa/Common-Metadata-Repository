(ns cmr.search.test.site.data
  (:require
   [clojure.test :refer :all]
   [cmr.search.site.data :as data]
   [cmr.transmit.config :as config]))

(def cmr-base-url "http://cmr.test.host/")

(def coll-data-1
  {:concept-id "C1200000003-PROV1"
   :short-name "s3"
   :entry-title "coll3"
   :version-id "6"})

(def coll-data-2
  {:concept-id "C1200000003-PROV1"
   :short-name "s3"
   :entry-title "coll3"
   :version-id "7"
   :doi "doi6"})

(deftest make-holding-data
  (testing "with an entry title and short name"
    (let [data (data/make-holding-data cmr-base-url coll-data-1)]
      (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html" (:canonical-link-href data)))
      (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html" (:cmr-link-href data)))
      (is (= "coll3" (:entry-title data)))
      (is (= "s3" (:short-name data)))
      (is (= "6" (:version-id data)))))
  (testing "with an entry title, short name, and doi"
    (let [data (data/make-holding-data cmr-base-url coll-data-2)]
      (is (= "https://doi.org/doi6" (:canonical-link-href data)))
      (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html" (:cmr-link-href data)))
      (is (= "s3" (:short-name data)))
      (is (= "7" (:version-id data))))))

(deftest make-holdings-data
  (let [data (->> [coll-data-1 coll-data-2]
                  (data/make-holdings-data cmr-base-url)
                  (vec))]
    (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html" (get-in data [0 :canonical-link-href])))
    (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html" (get-in data [0 :cmr-link-href])))
    (is (= "s3" (get-in data [0 :short-name])))
    (is (= "6" (get-in data [0 :version-id])))
    (is (= "https://doi.org/doi6" (get-in data [1 :canonical-link-href])))
    (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html" (get-in data [1 :cmr-link-href])))
    (is (= "s3" (get-in data [1 :short-name])))
    (is (= "7" (get-in data [1 :version-id])))))
