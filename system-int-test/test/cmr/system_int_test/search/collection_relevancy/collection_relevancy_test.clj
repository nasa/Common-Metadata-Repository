(ns cmr.system-int-test.search.collection-relevancy.collection-relevancy-test
  "Test the integration between different kinds of relevancy: Keyword, Temporal, and
   Community Usage."
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.config :as config]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.data.elastic-relevancy-scoring :as elastic-relevancy-scoring]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-common]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture]))

(def sample-usage-csv
  (str "Product,ProductVersion,Hosts\n"
       "Usage-10,3,10\n"
       "Usage-100,1,100\n"
       "Usage-30,2,30\n"
       "Usage-130,1,130\n"))

(deftest relevancy-temporal-ranges
  (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-sort-use-relevancy-score! true))
  (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-sort-use-temporal-relevancy! true))
  (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-sort-bin-keyword-scores! false))
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "Usage-30"
                 :Version "2"
                 :EntryTitle "Elevation coll1"
                 :Platforms [(data-umm-c/platform {:ShortName "Usage"})]
                 :TemporalExtents [(data-common/temporal-extent
                                    {:beginning-date-time "2003-08-01T00:00:00Z"
                                     :ending-date-time "2005-10-01T00:00:00Z"})]}))
        coll2 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "Usage-100"
                 :Version "1"
                 :EntryTitle "Elevation coll2"
                 :TemporalExtents [(data-common/temporal-extent
                                    {:beginning-date-time "2001-08-01T00:00:00Z"
                                     :ending-date-time "2010-10-01T00:00:00Z"})]}))
        coll3 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:ShortName "Usage-10"
                 :Version "3"
                 :EntryTitle "Elevation coll3"
                 :TemporalExtents [(data-common/temporal-extent
                                    {:beginning-date-time "2002-10-15T12:00:00Z"
                                     :ends-at-present? true})]}))]
    (index/wait-until-indexed)

    (testing "Keyword and temporal"
      (are3 [expected-collections search-params]
        (is (d/refs-match-order? expected-collections (search/find-refs :collection search-params)))

        "Keyword search baseline"
        [coll3 coll2 coll1]
        {:keyword "Elevation"}

        "Keyword tie breaker temporal"
        [coll3 coll2 coll1]
        {:keyword "Elevation" :temporal ["2005-01-01T10:00:00Z,2011-03-01T0:00:00Z"]}

        "Equal temporal and keyword"
        [coll3 coll2 coll1]
        {:keyword "Elevation" :temporal ["2004-01-01T10:00:00Z,2005-03-01T0:00:00Z"]}

        "Keyword takes precedence over temporal"
        [coll1 coll3 coll2]
        {:keyword "Usage" :temporal ["2004-01-01T10:00:00Z,2015-03-01T0:00:00Z"]}))

   (testing "Keyword, temporal, and usage"
     ;; No binning of community usage
     (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-community-usage-bin-size! 1))
     (hu/ingest-community-usage-metrics sample-usage-csv)

     (are3 [expected-collections search-params]
       (is (d/refs-match-order? expected-collections (search/find-refs :collection search-params)))

       "Equal temporal and keyword, community usage tie breaker"
       [coll2 coll1 coll3] {:keyword "Elevation"
                            :temporal ["2004-01-01T10:00:00Z,2005-03-01T0:00:00Z"]}

       "Temporal takes precedence over community usage"
       [coll3 coll2 coll1] {:keyword "Elevation"
                            :temporal ["2005-01-01T10:00:00Z,2011-03-01T0:00:00Z"]}))

   (testing "Keyword and usage"
     (are3 [expected-collections search-params]
       (is (d/refs-match-order? expected-collections (search/find-refs :collection search-params)))

       "Equal keyword, community usage tie breaker"
       [coll2 coll1 coll3] {:keyword "Elevation"}

       "Keyword takes precedence over community usage"
       [coll1 coll2 coll3] {:keyword "Usage"}))

   (testing "Keyword score binning"
     ;; If the scores change here, this test will either fail or become useless and
     ;; will have to be rewritten
     (is (= [0.65 0.5 0.5]
            (map :score (:refs (search/find-refs :collection {:keyword "Usage"})))))

     (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-sort-bin-keyword-scores! true))

     (testing "bin size 0.1, same order"
       (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-keyword-score-bin-size! 0.1))
       (is (d/refs-match-order? [coll1 coll2 coll3]
                                (search/find-refs :collection {:keyword "Usage"}))))

     (testing "bin size 0.2, order by usage"
       (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-keyword-score-bin-size! 0.2))
       (is (d/refs-match-order? [coll2 coll1 coll3]
                                (search/find-refs :collection {:keyword "Usage"}))))

     (testing "bin size 0.3 - same as 0.2"
       (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-keyword-score-bin-size! 0.3))
       (is (d/refs-match-order? [coll2 coll1 coll3]
                                (search/find-refs :collection {:keyword "Usage"})))))))

(deftest community-usage-binning
  (let [coll-usage-10 (d/ingest-umm-spec-collection
                       "PROV1"
                       (data-umm-c/collection
                        {:ShortName "Usage-10"
                         :Version "3"
                         :EntryTitle "Usage-10"
                         :TemporalExtents [(data-common/temporal-extent
                                            {:beginning-date-time "2001-08-01T00:00:00Z"
                                             :ending-date-time "2004-01-01T00:00:00Z"})]}))
        coll-usage-30 (d/ingest-umm-spec-collection
                       "PROV1"
                       (data-umm-c/collection
                        {:ShortName "Usage-30"
                         :Version "2"
                         :EntryTitle "Usage-30"
                         :TemporalExtents [(data-common/temporal-extent
                                            {:beginning-date-time "2001-08-01T00:00:00Z"
                                             :ending-date-time "2003-01-01T00:00:00Z"})]}))
        coll-usage-100 (d/ingest-umm-spec-collection
                        "PROV1"
                        (data-umm-c/collection
                         {:ShortName "Usage-100"
                          :Version "1"
                          :EntryTitle "Usage-100"
                          :TemporalExtents [(data-common/temporal-extent
                                             {:beginning-date-time "2001-08-01T00:00:00Z"
                                              :ending-date-time "2002-01-01T00:00:00Z"})]}))
        coll-usage-130 (d/ingest-umm-spec-collection
                        "PROV1"
                        (data-umm-c/collection
                         {:ShortName "Usage-130"
                          :Version "1"
                          :EntryTitle "Usage-130"
                          :TemporalExtents [(data-common/temporal-extent
                                             {:beginning-date-time "2000-08-01T00:00:00Z"
                                              :ending-date-time "2001-01-01T00:00:00Z"})]}))
        coll-no-usage (d/ingest-umm-spec-collection
                       "PROV1"
                       (data-umm-c/collection
                        {:ShortName "No-usage"
                         :Version "3"
                         :EntryTitle "No-usage"
                         :TemporalExtents [(data-common/temporal-extent
                                            {:beginning-date-time "2001-08-01T00:00:00Z"
                                             :ends-at-present? true})]}))]
    (hu/ingest-community-usage-metrics sample-usage-csv)
    (testing "Community usage binning"
      (testing "All in different bins returns by community usage order."
        (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-community-usage-bin-size! 1))
        (is (d/refs-match-order?
             [coll-usage-130 coll-usage-100 coll-usage-30 coll-usage-10 coll-no-usage]
             (search/find-refs :collection {:keyword "Usage"}))))
      (testing "All in the same bin returns by temporal end date."
        (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-community-usage-bin-size! 1000))
        (is (d/refs-match-order?
             [coll-no-usage coll-usage-10 coll-usage-30 coll-usage-100 coll-usage-130]
             (search/find-refs :collection {:keyword "Usage"}))))
      (testing "Usage 100 and 130 binned together and no usage, 10, and 30 binned together"
        (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-community-usage-bin-size! 100))
        (is (d/refs-match-order?
             [coll-usage-100 coll-usage-130 coll-no-usage coll-usage-10 coll-usage-30]
             (search/find-refs :collection {:keyword "Usage"})))))))

;; Collections with the same keyword score, community usage, and end date
;; should be sorted by humanized processing level descending
(deftest relevancy-processing-level
  (let [coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             {:ShortName "Elevation L1"
                                              :Version "2"
                                              :EntryTitle "Elevation coll1"
                                              ;; Will get humanized to 1T
                                              :ProcessingLevel {:Id "L1T"}}))
        coll2 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             {:ShortName "Elevation L1"
                                              :Version "1"
                                              :EntryTitle "Elevation coll2"
                                              :ProcessingLevel {:Id "2"}}))
        coll3 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             {:ShortName "Elevation L4"
                                              :Version "4"
                                              :EntryTitle "Elevation coll3"
                                              :ProcessingLevel {:Id "4"}}))
        coll4 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             {:ShortName "Elevation NP"
                                              :Version "5"
                                              :EntryTitle "Elevation coll4"
                                              :ProcessingLevel {:Id "Not provided"}}))]
    (index/wait-until-indexed)
    (is (d/refs-match-order? [coll3 coll2 coll1 coll4]
                             (search/find-refs :collection {:keyword "Elevation"})))
    (search/find-refs :collection {:processing-level-id-h "1T"})))
