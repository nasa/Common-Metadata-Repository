(ns cmr.transmit.test.echo.conversion
  (:require [clojure.test :refer :all]
            [cmr.transmit.echo.conversion :as c]
            [clj-time.core :as t]))

(deftest parse-and-generate-echo-temporal-dates
  (testing "parsing with UTC"
    (is (= (t/date-time 2010 1 1)
           (#'c/parse-echo-temporal-date "Fri Jan 01 00:00:00 UTC 2010"))))
  (testing "parsing with in another timezone"
    (is (= (t/date-time 2015 9 1 16 22 41)
           (#'c/parse-echo-temporal-date "Tue Sep 01 12:22:41 -0400 2015")))))

(def echo-collection-identifier
  {:collection_ids [{:data_set_id "Landsat 1-5 Multispectral Scanner V1"}
                    {:data_set_id "Landsat 4-5 Thematic Mapper V1"}]
   :restriction_flag {:include_undefined_value false, :max_value 3.0, :min_value 3.0}
   :temporal {:start_date "Fri Jan 01 00:00:00 +0000 2010"
              :stop_date "Tue Sep 01 12:22:41 +0000 2015"
              :mask "DISJOINT",
              :temporal_field "ACQUISITION"}})

(def cmr-collection-identifier
  {:entry-titles ["Landsat 1-5 Multispectral Scanner V1"
                  "Landsat 4-5 Thematic Mapper V1"]
   :access-value {:include-undefined false, :max-value 3.0, :min-value 3.0}
   :temporal {:start-date (t/date-time 2010 1 1)
              :end-date (t/date-time 2015 9 1 12 22 41)
              :mask :disjoint,
              :temporal-field :acquisition}})

(def echo-granule-identifier
  {:restriction_flag {:include_undefined_value true
                      :max_value 5.0, :min_value 3.0}
   :temporal {:start_date "Fri Jan 01 00:00:00 +0000 2010"
              :stop_date "Tue Sep 01 12:22:41 +0000 2015"
              :mask "DISJOINT",
              :temporal_field "ACQUISITION"}})

(def cmr-granule-identifier
  {:access-value {:include-undefined true, :max-value 5.0, :min-value 3.0}
   :temporal {:start-date (t/date-time 2010 1 1)
              :end-date (t/date-time 2015 9 1 12 22 41)
              :mask :disjoint,
              :temporal-field :acquisition}})

(def echo-acl
  {:acl {:id "5C1B77E7-48E5-4579-E516-7D933F500F23"
         :access_control_entries [{:permissions ["ORDER" "READ"],
                                   :sid {:group_sid {:group_guid "3730376E-4DCF-53EE-90ED-FE945351A64F"}}}
                                  {:permissions ["READ"],
                                   :sid {:user_authorization_type_sid {:user_authorization_type "GUEST"}}}
                                  {:permissions ["READ"],
                                   :sid {:user_authorization_type_sid {:user_authorization_type "REGISTERED"}}}],
         :catalog_item_identity {:collection_applicable false,
                                 :granule_applicable true,
                                 :name "All Granules",
                                 :provider_guid "CB91244B-C8B7-CA27-3089-3EC721FFF4D8"
                                 :collection_identifier echo-collection-identifier
                                 :granule_identifier echo-granule-identifier}}})

(def cmr-acl
  {:guid "5C1B77E7-48E5-4579-E516-7D933F500F23"
   :aces [{:permissions [:order :read],
           :group-guid "3730376E-4DCF-53EE-90ED-FE945351A64F"}
          {:permissions [:read]
           :user-type :guest}
          {:permissions [:read]
           :user-type :registered}]
   :catalog-item-identity {:collection-applicable false,
                           :granule-applicable true,
                           :name "All Granules",
                           :provider-guid "CB91244B-C8B7-CA27-3089-3EC721FFF4D8"
                           :collection-identifier cmr-collection-identifier
                           :granule-identifier cmr-granule-identifier}})

(deftest conversion-test
  (testing "echo -> cmr"
    (is (= cmr-acl (c/echo-acl->cmr-acl echo-acl))))
  (testing "cmr -> echo"
    (is (= echo-acl (c/cmr-acl->echo-acl cmr-acl))))

  (testing "sids by themselves"
    (are [cmr-sid echo-sid]
         (and (= cmr-sid (c/echo-sid->cmr-sid echo-sid))
              (= echo-sid (c/cmr-sid->echo-sid cmr-sid)))
         :guest {:sid {:user_authorization_type_sid {:user_authorization_type "GUEST"}}}
         :registered {:sid {:user_authorization_type_sid {:user_authorization_type "REGISTERED"}}}
         "group-guid" {:sid {:group_sid {:group_guid "group-guid"}}})))
