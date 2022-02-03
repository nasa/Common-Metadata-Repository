(ns cmr.system-int-test.search.collection-consortium-search-test
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
                        :short-name "PROVIDER 3"}]))

(deftest collection-consortium-search-test
  (ingest/delete-provider "PROV1")
  (ingest/delete-provider "PROV2")
  (ingest/delete-provider "PROV3")
  (ingest/create-provider {:provider-guid "provguid_consortium1" :provider-id "PROV1" :consortiums "consortium11 consortium12"})
  (ingest/create-provider {:provider-guid "provguid_consortium2" :provider-id "PROV2" :consortiums "consortium21 consortium22"})
  (ingest/create-provider {:provider-guid "provguid_consortium2" :provider-id "PROV3" :consortiums "geoss"})

  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 1
                                 {:CollectionCitations [{:Creator "K. Hilburn"}
                                                        {:Creator "Nelkin"}]})
               {:format :umm-json})
        coll2 (d/ingest-umm-spec-collection
               "PROV2"
               (umm-c/collection 2
                                 {:CollectionCitations [{:Creator "K. Hilburn, J. Ardizzone, and S. Gao"
                                                         :OtherCitationDetails "Sounder PEATE (Product Evaluation and Test Element) Team/Ruth Monarrez, JPL"}]})
               {:format :umm-json})
        coll3 (d/ingest-umm-spec-collection
               "PROV3"
               (umm-c/collection 3 
                                 {:UseConstraints {:Description "PROV4" :FreeAndOpenData false}})
               {:format :umm-json})
        id1 (:concept-id coll1)
        id2 (:concept-id coll2)
        id3 (:concept-id coll3)]

    (index/wait-until-indexed)

    (testing "consortium returned in json and atom responses"
      (let [consortiums-in-json (-> (search/find-concepts-json :collection {:concept-id id1})
                                    (get-in [:results :entries])
                                    first
                                    :consortiums)
            consortiums-in-atom (-> (search/find-concepts-atom :collection {:concept-id id2})
                                    (get-in [:results :entries])
                                    first
                                    :consortiums)
            consortiums-in-json3 (-> (search/find-concepts-json :collection {:concept-id id3})
                                     (get-in [:results :entries])
                                     first
                                     :consortiums)]
        (is (= ["CONSORTIUM11" "CONSORTIUM12"] consortiums-in-json))
        (is (= ["CONSORTIUM21" "CONSORTIUM22"] consortiums-in-atom))
        ;; Fixed for CMR-7895. In this case, geoss is removed from coll3's consortiums,
        ;; empty consortiums should not be shown as an empty list [] in the json response.
        (is (= nil consortiums-in-json3))))

    (testing "consortium parameter search"
      (are3 [items consortium options]
        (let [params (merge {:consortium consortium}
                            options)]
          (d/refs-match? items (search/find-refs :collection params)))

        "consortium search1"
        [coll1] "consortium11" nil

        "consortium search2 ignore case"
        [coll2] "ConSortium21" nil

        "Pattern search"
        [coll1 coll2] "*consortium*" {"options[consortium][pattern]" "true"}

        "Pattern search, leading wildcard only"
        [coll1] "*sortium11" {"options[consortium][pattern]" "true"}

        "And search"
        [coll1] ["*sortium11" "consortium12"] {"options[consortium][pattern]" "true"
                                               "options[consortium][and]" "true"}

        "Or search"
        [coll1 coll2] ["consortium11" "consortium21"] nil))))
