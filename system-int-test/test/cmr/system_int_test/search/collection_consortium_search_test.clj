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


(deftest collection-consortium-search-test
  (ingest/delete-provider "PROV1")
  (ingest/delete-provider "PROV2")
  (ingest/create-provider {:provider-guid "provguid_cst1" :provider-id "PROV1" :consortiums "cst11 cst12"})
  (ingest/create-provider {:provider-guid "provguid_cst2" :provider-id "PROV2" :consortiums "cst21 cst22"})

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
        id1 (:concept-id coll1)
        id2 (:concept-id coll2)]

    (index/wait-until-indexed)

    (testing "consortium returned in json and atom responses"
      (let [consortiums-in-json (-> (search/find-concepts-json :collection {:concept-id id1})
                                    (get-in [:results :entries])
                                    first
                                    :consortiums)
            consortiums-in-atom (-> (search/find-concepts-atom :collection {:concept-id id2})
                                    (get-in [:results :entries])
                                    first
                                    :consortiums)]
        (is (= ["cst11" "cst12"] consortiums-in-json))
        (is (= ["cst21" "cst22"] consortiums-in-atom))))
     
    (testing "consortium parameter search"
      (are3 [items consortium options]
        (let [params (merge {:consortium consortium}
                            options)]
          (d/refs-match? items (search/find-refs :collection params)))

        "consortium search1"
        [coll1] "cst11" nil

        "consortium search2 ignore case"
        [coll2] "CsT21" nil

        "Pattern search"
        [coll1 coll2] "*cst*" {"options[consortium][pattern]" "true"}

        "Pattern search, leading wildcard only"
        [coll1] "*11" {"options[consortium][pattern]" "true"}

        "And search"
        [coll1] ["*st11" "cst12"] {"options[consortium][pattern]" "true"
                                   "options[consortium][and]" "true"}

        "Or search"
        [coll1 coll2] ["cst11" "cst21"] nil))))
