(ns cmr.system-int-test.search.collection-author-search-test
  "Integration tests for collection author search (collection citations creator)"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as umm-c]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest collection-author-search-test
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 1
                                 {:CollectionCitations [{:Creator "K. Hilburn"}
                                                        {:Creator "Nelkin"}]})
               {:format :umm-json})
        coll2 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 2
                                 {:CollectionCitations [{:Creator "K. Hilburn, J. Ardizzone, and S. Gao"
                                                         :OtherCitationDetails "Sounder PEATE (Product Evaluation and Test Element) Team/Ruth Monarrez, JPL"}]})
               {:format :umm-json})]

    (index/wait-until-indexed)

    (testing "Author parameter search"
      (are3 [items author options]
        (let [params (merge {:author author}
                            options)]
          (d/refs-match? items (search/find-refs :collection params)))

        "Author search"
        [coll1] "Nelkin" nil

        "Pattern search"
        [coll1 coll2] "*hilburn*" {"options[author][pattern]" "true"}

        "Pattern search, leading wildcard only"
        [coll1] "*Hilburn" {"options[author][pattern]" "true"}

        "And search"
        [coll1] ["*Hilburn*" "Nelkin"] {"options[author][pattern]" "true"
                                        "options[author][and]" "true"}

        "Other citation details search"
        [coll2] "*jpl*" {"options[author][pattern]" "true"})

     (testing "Keyword search"
       (are3 [items keyword options]
         (let [params (merge {:keyword keyword}
                             options)]
           (d/refs-match? items (search/find-refs :collection params)))

         "Author search"
         [coll1] "Nelkin" nil

         "Search returns multiple collections"
         [coll1 coll2] "*hilburn*" {"options[keyword][pattern]" "true"}

         "Other citation details search"
         [coll2] "JPL*" {"options[keyword][pattern]" "true"})))))
