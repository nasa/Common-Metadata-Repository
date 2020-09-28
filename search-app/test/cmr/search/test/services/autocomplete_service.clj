(ns cmr.search.test.services.autocomplete-service
  (:require
   [clojure.test :refer :all]
   [cmr.search.services.autocomplete-service :as autocomplete-service]))

(def unique-acls
  [{:group-permissions [{:permissions ["read"]
                         :group-id "AG1200000019-CMR"}]
    :catalog-item-identity {:name "6c5102f4-f171-45d8-98a7-0abb89a58d3b"
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Secret Collection"]
                                                    :concept-ids ["C1200000015-PROV2"]}
                            :collection-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :user-type "registered"}]
    :catalog-item-identity {:name "cc73bd70-2a04-4083-bd7b-53ff0f932c71",
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Registered Collection"]
                                                    :concept-ids ["C1200000016-PROV2"]}
                            :collection-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :user-type "guest"} {:permissions ["read"]
                                              :user-type "registered"}]
    :catalog-item-identity {:name "e638d705-1a0d-4ab3-ac38-78d90bb2090d"
                            :provider-id "PROV1"
                            :collection-applicable true
                            :granule-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :group-id "AG1200000018-CMR"}]
    :catalog-item-identity {:name "fdd9fa2d-691b-4658-9e1b-e693240957b6"
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Secret Collection"]
                                                    :concept-ids ["C1200000015-PROV2"]}
                            :collection-applicable true}}])

(def non-unique-acls
  [{:group-permissions [{:permissions ["read"]
                         :group-id "AG1200000019-CMR"}]
    :catalog-item-identity {:name "6c5102f4-f171-45d8-98a7-0abb89a58d3b"
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Another Secret Collection"]
                                                    :concept-ids ["C1200000021-PROV2"]}
                            :collection-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :group-id "AG1200000019-CMR"}]
    :catalog-item-identity {:name "6c5102f4-f171-45d8-98a7-0abb89a58d3b"
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Secret Collection"]
                                                    :concept-ids ["C1200000015-PROV2"]}
                            :collection-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :user-type "registered"}]
    :catalog-item-identity {:name "cc73bd70-2a04-4083-bd7b-53ff0f932c71",
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Registered Collection"]
                                                    :concept-ids ["C1200000016-PROV2"]}
                            :collection-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :user-type "guest"} {:permissions ["read"]
                                              :user-type "registered"}]
    :catalog-item-identity {:name "e638d705-1a0d-4ab3-ac38-78d90bb2090d"
                            :provider-id "PROV1"
                            :collection-applicable true
                            :granule-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :group-id "AG1200000018-CMR"}]
    :catalog-item-identity {:name "fdd9fa2d-691b-4658-9e1b-e693240957b6"
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Secret Collection"]
                                                    :concept-ids ["C1200000015-PROV2"]}
                            :collection-applicable true}}
   {:group-permissions [{:permissions ["read"]
                         :group-id "AG1200000018-CMR"}]
    :catalog-item-identity {:name "fdd9fa2d-691b-4658-9e1b-e693240957b6"
                            :provider-id "PROV2"
                            :collection-identifier {:entry-titles ["Another Secret Collection"]
                                                    :concept-ids ["C1200000021-PROV2"]}
                            :collection-applicable true}}])
(deftest acl-group-id-test
  (testing "Ensure that group-ids are distinct"
    (is (= ["AG1200000018-CMR" "AG1200000019-CMR" "registered"]
           (autocomplete-service/fetch-group-ids-for-autocomplete unique-acls)))
    (is (= ["AG1200000018-CMR" "AG1200000019-CMR" "registered"]
           (autocomplete-service/fetch-group-ids-for-autocomplete non-unique-acls)))))
