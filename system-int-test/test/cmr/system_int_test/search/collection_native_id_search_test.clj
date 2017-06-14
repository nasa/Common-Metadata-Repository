(ns cmr.system-int-test.search.collection-native-id-search-test
  "Integration test for CMR collection search by doi"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-native-id
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "NativeId1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "NativeId2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))]
    (index/wait-until-indexed)

    (are3 [items search]
      (is (d/refs-match? items (search/find-refs :collection search)))

      "search by non-existent native id."
      [] {:native-id "NON_EXISTENT"}

      "search by existing native id."
      [coll1] {:native-id "NativeId1"}

      "search by native-id using wildcard *."
      [coll1 coll2] {:native-id "Native*" "options[native-id][pattern]" "true"}

      "search by native-id using wildcard ?."
      [coll1 coll2] {:native-id "NativeId?" "options[native-id][pattern]" "true"}

      "search by native-id defaut is ignore case true."
      [coll1] {:native-id "nativeid1"}

      "search by native-id ignore case false"
      []{:native-id "nativeid1" "options[native-id][ignore-case]" "false"}

      "search by native-id ignore case true."
      [coll1] {:native-id "nativeid1" "options[native-id][ignore-case]" "true"})))

