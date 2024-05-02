(ns cmr.system-int-test.search.collection-consortium-geoss-search-test
  "Integration tests for collection consortium search"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as umm-c]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture
                      [{:provider-guid "provguid1"
                        :provider-id "PROV1"
                        :short-name "Provider 1"}
                       {:provider-guid "provguid2"
                        :provider-id "PROV2"
                        :short-name "PROVIDER 2"}
                       {:provider-guid "provguid3"
                        :provider-id "PROV3"
                        :short-name "PROVIDER 3"}
                       {:provider-guid "provguid4"
                        :provider-id "PROV4"
                        :short-name "PROVIDER 4"}
                       {:provider-guid "provguid5"
                        :provider-id "PROV5"
                        :short-name "PROVIDER 5"}
                       {:provider-guid "provguid6"
                        :provider-id "PROV6"
                        :short-name "PROVIDER 6"}]))

(deftest collection-consortium-geoss-search-test
  (ingest/delete-provider "PROV1")
  (ingest/delete-provider "PROV2")
  (ingest/delete-provider "PROV3")
  (ingest/delete-provider "PROV4")
  (ingest/delete-provider "PROV5")
  (ingest/delete-provider "PROV6")
  (ingest/create-provider {:provider-guid "provguid_no_geoss_eosdis":provider-id "PROV1" :consortiums "eosdis cstg11 cstg12"})
  (ingest/create-provider {:provider-guid "provguid_no_geoss_no_eosdis." :provider-id "PROV2" :consortiums "cstg21 cstg22"})
  (ingest/create-provider {:provider-guid "provguid_no_geoss_fod_true." :provider-id "PROV3" :consortiums "cstg31 cstg32"})
  (ingest/create-provider {:provider-guid "provguid_geoss_fod_false" :provider-id "PROV4" :consortiums "geoss cstg41 cstg42"})
  (ingest/create-provider {:provider-guid "provguid_no_geoss_fod_nil_with_geossurl" :provider-id "PROV5" :consortiums "cstg51 cstg52"})
  (ingest/create-provider {:provider-guid "provguid_no_geoss_fod_nil_no_geosurl" :provider-id "PROV6" :consortiums "cstg61 cstg62"})

  (let [;; no UseConstraint, eosdis provider: geoss should be added to the consortiums list.
        coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 1
                                 {:CollectionCitations [{:Creator "K. Hilburn"}
                                                        {:Creator "Nelkin"}]})
               {:format :umm-json})
        ;; no UseConstraint, not eosdis provider: no geoss should be added to the consortums list. 
        coll2 (d/ingest-umm-spec-collection
               "PROV2"
               (umm-c/collection 1 
                                 {:CollectionCitations [{:Creator "K. Hilburn, J. Ardizzone, and S. Gao"
                                                         :OtherCitationDetails "Sounder PEATE (Product Evaluation and Test Element) Team/Ruth Monarrez, JPL"}]})
               {:format :umm-json})

        ;; UseConstraint with FreeAndOpenData being true, no GEOSS provider: geoss should be added to the consortiums list.
        coll3 (d/ingest-umm-spec-collection
               "PROV3"
               (umm-c/collection 1
                                 {:UseConstraints {:Description "PROV3" :FreeAndOpenData true}})
               {:format :umm-json})

        ;; UseConstraint with FreeAndOpenData being false, GEOSS provder: geoss should be removed from the consortiums list.
        coll4 (d/ingest-umm-spec-collection
               "PROV4"
               (umm-c/collection 1
                                 {:UseConstraints {:Description "PROV4" :FreeAndOpenData false}})
               {:format :umm-json})

        ;; UseConstraint with FreeAndOpenData being nil, and GEOSS url: geoss should be added to the consortiums list.
        coll5 (d/ingest-umm-spec-collection
               "PROV5"
               (umm-c/collection 1
                                 {:UseConstraints {:Description "PRO5" :LicenseURL {:Linkage "http://creativecommons.org/licenses/by/4.0/"}}})
               {:format :umm-json})

        ;; UseConstraint with FreeAndOpenData being nil, no GEOSS url: no geoss should be added to the consortiums list.
        coll6 (d/ingest-umm-spec-collection
               "PROV6"
               (umm-c/collection 1
                                 {:UseConstraints {:Description "PRO6" :LicenseURL {:Linkage "http://not-geoss-url"}}})
               {:format :umm-json})]

    (index/wait-until-indexed)

    (testing "consortium parameter geoss search"
      (are3 [items consortium options]
        (let [params (merge {:consortium consortium}
                            options)]
          (d/refs-match? items (search/find-refs :collection params)))

        "geoss search"
        [coll1 coll3 coll5] "geoss" nil

        ;; More complete non geoss search is in collection_consortium_search_test.clj
        "non geoss search1"
        [coll2] "cstg21" nil

        "non geoss search2"
        [coll4] "cstg41" nil

        "non geoss search3"
        [coll6] "cstg61" nil))))
