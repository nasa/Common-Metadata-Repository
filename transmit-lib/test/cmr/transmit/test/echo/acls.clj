(ns cmr.transmit.test.echo.acls
  "Tests for cmr.transmit.echo.acls namespace"
  (:require [clojure.test :refer :all]
            [cmr.transmit.echo.acls :as a]))

(def good-acls
  [{:guid "5F"
    :aces [{:permissions [:update :read] :group-guid "55"}]
    :provider-object-identity {:provider-id "GES_DISC" :target "INGEST_MANAGEMENT_ACL"}}
   {:guid "79"
    :aces [{:permissions [:create :read] :group-guid "67"}]
    :system-object-identity {:target "GROUP"}}
   {:guid "5C"
    :aces [{:permissions [:read] :user-type :registered}
           {:permissions [:read] :user-type :guest}]
    :catalog-item-identity {:provider-id "ASF"
                            :collection-applicable false
                            :granule-applicable true
                            :granule-identifier {}
                            :name "All Granules"}}
   {:guid "FB"
    :aces [{:permissions []
            :group-guid "FD220A06-0279-D06C-135A-8757C97C1C2B"}
           {:permissions [] :user-type :registered}
           {:permissions [] :user-type :guest}]
    :catalog-item-identity {:provider-id "ASF"
                            :collection-applicable true
                            :collection-identifier {:entry-titles
                                                    ["R1_SCANSAR_SWATH"
                                                     "R1_STD_FRAME"]}
                            :granule-applicable true
                            :granule-identifier {}
                            :name "All Collections (Individually Selected)"}}
   {:guid "30"
    :aces [{:permissions [:create :delete] :group-guid "7E"}]
    :provider-object-identity {:provider-id "OPS_ECHO10"
                               :target "DATA_QUALITY_SUMMARY_ASSIGNMENT"}}])


(deftest validate-and-filter-acls
  (testing "good acls"
    (is (= good-acls (#'a/validate-and-filter-acls good-acls))))
  (testing "bad acls"
    (let [bad-acls (map #(assoc % :foo "blah") good-acls)
          mix-acls (concat bad-acls good-acls bad-acls)]
      (is (= good-acls (#'a/validate-and-filter-acls mix-acls))))))

