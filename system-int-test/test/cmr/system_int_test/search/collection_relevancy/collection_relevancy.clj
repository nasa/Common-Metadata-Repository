(ns cmr.system-int-test.search.collection-relevancy.collection-relevancy
  "Test the integration between different kinds of relevancy: Keyword, Temporal, and
   Community Usage."
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.config :as config]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.data.query-to-elastic :as query-to-elastic]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def sample-usage-csv
  (str "Product,Version,Hosts\n"
       "Usage-10,3,10\n"
       "Usage-100,1,100\n"
       "Usage-30,2,30\n"))

(deftest relevancy-temporal-ranges
  (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "Usage-30"
                                                :version-id "2"
                                                :entry-title "Elevation coll1"
                                                :platforms [(dc/platform {:short-name "Usage"})]
                                                :temporal (dc/temporal {:beginning-date-time "2003-08-01T00:00:00Z"
                                                                        :ending-date-time "2005-10-01T00:00:00Z"})}))
        coll2 (d/ingest "PROV1" (dc/collection {:short-name "Usage-100"
                                                :version-id "1"
                                                :entry-title "Elevation coll2"
                                                :temporal (dc/temporal {:beginning-date-time "2001-08-01T00:00:00Z"
                                                                        :ending-date-time "2010-10-01T00:00:00Z"})}))
        coll3 (d/ingest "PROV1" (dc/collection {:short-name "Usage-10"
                                                :version-id "3"
                                                :entry-title "Elevation coll3"
                                                :temporal (dc/temporal {:beginning-date-time "2002-10-15T12:00:00Z"
                                                                        :ends-at-present? true})}))]
    (index/wait-until-indexed)

    (testing "Keyword and temporal"
      (are3 [expected-collections search-params]
        (is (d/refs-match-order? expected-collections (search/find-refs :collection search-params)))

        "Keyword search baseline"
        [coll3 coll2 coll1] {:keyword "Elevation"}

        "Keyword tie breaker temporal"
        [coll3 coll2 coll1] {:keyword "Elevation" :temporal ["2005-01-01T10:00:00Z,2011-03-01T0:00:00Z"]}

        "Equal temporal and keyword"
        [coll3 coll2 coll1] {:keyword "Elevation" :temporal ["2004-01-01T10:00:00Z,2005-03-01T0:00:00Z"]}

        "Keyword takes precedence over temporal"
        [coll1 coll3 coll2] {:keyword "Usage" :temporal ["2004-01-01T10:00:00Z,2015-03-01T0:00:00Z"]}))

   (testing "Keyword, temporal, and usage"
     (hu/ingest-community-usage-metrics sample-usage-csv)

     (are3 [expected-collections search-params]
       (is (d/refs-match-order? expected-collections (search/find-refs :collection search-params)))

       "Equal temporal and keyword, community usage tie breaker"
       [coll2 coll1 coll3] {:keyword "Elevation" :temporal ["2004-01-01T10:00:00Z,2005-03-01T0:00:00Z"]}

       "Temporal takes precedence over community usage"
       [coll3 coll2 coll1] {:keyword "Elevation" :temporal ["2005-01-01T10:00:00Z,2011-03-01T0:00:00Z"]}))

   (testing "Keyword and usage"
     (are3 [expected-collections search-params]
       (is (d/refs-match-order? expected-collections (search/find-refs :collection search-params)))

       "Equal keyword, community usage tie breaker"
       [coll2 coll1 coll3] {:keyword "Elevation"}

       "Keyword takes precedence over community usage"
       [coll1 coll2 coll3] {:keyword "Usage"}))))
