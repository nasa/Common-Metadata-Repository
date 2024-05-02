(ns cmr.system-int-test.search.collection-eula-json-format-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.system-int-test.data2.core :as data2]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-collections-eula-identifiers
  (let [_coll1 (data2/ingest-umm-spec-collection "PROV1"
                                                (data-umm-c/collection 1
                                                                       {:EntryTitle "Dataset1"
                                                                        :UseConstraints {:Description "EULA"
                                                                                         :LicenseURL {:Linkage "https://somelicenseurl.org",
                                                                                                      :Name "License URL",
                                                                                                      :Description "This is the License URL for this data set.",
                                                                                                      :MimeType "text/html"}
                                                                                         :EULAIdentifiers ["EulaIdentifier1" "EulaIdentifier2"]}})
                                                {:format :umm-json})
        _coll2 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {}))
        _ (index/wait-until-indexed)
        {json-status :status json-results :results} (search/find-concepts-json
                                                     :collection {:provider "PROV1"})]
    (is (= 200 json-status))
    (is (= ["EulaIdentifier1" "EulaIdentifier2"] (-> json-results :entries first :eula_identifiers)))
    (is (= nil (-> json-results :entries second :eula_identifiers)))))
