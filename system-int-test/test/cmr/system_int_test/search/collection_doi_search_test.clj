(ns cmr.system-int-test.search.collection-doi-search-test
  "Integration test for CMR collection search by doi"
  (:require 
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm-spec.models.umm-common-models :as cm]
    [cmr.umm-spec.test.expected-conversion :as exp-conv]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-doi
  (let [coll1 (d/ingest "PROV1"
                        (-> exp-conv/example-collection-record
                            (assoc :DOI (cm/map->DoiType
                                         {:DOI "doi" :Authority "auth"})))
                        {:format :umm-json
                         :accept-format :json})] 
    (index/wait-until-indexed)

    (testing "search collections by doi"
      (are3 [items doi options]
            (let [params (merge {:doi doi}
                                (when options
                                  {"options[doi]" options}))]
              (d/refs-match? items (search/find-refs :collection params)))
       "search with doi"
       [coll1] "DoI" {}))))

