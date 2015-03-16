(ns cmr.system-int-test.search.collection-delete-time-search-test
  "This tests that collections whose delete times are in the past are properly cleaned up along
  with their granules"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.system :as s]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(comment

  (do
    (dev-sys-util/reset)
    (ingest/create-provider "provguid1" "PROV1")
    (ingest/create-provider "provguid2" "PROV2"))

)

(deftest collection-delete-time-test
  (s/only-with-in-memory-database
    (let [time-now (tk/now)
          make-coll (fn [prov entry-title num-secs-to-live]
                      (let [delete-time (when num-secs-to-live
                                          (str (t/plus time-now (t/seconds num-secs-to-live))))]
                        (d/ingest prov (dc/collection {:entry-title entry-title
                                                       :delete-time delete-time}))))
          make-gran (fn [coll granule-ur]
                      (d/ingest (:provider-id coll) (dg/granule coll {:granule-ur granule-ur})))

          ;; Collections that expire in 100 seconds
          coll1 (make-coll "PROV1" "coll1" 100)
          coll2 (make-coll "PROV1" "coll2" 100)
          coll3 (make-coll "PROV2" "coll3" 100)

          ;; Collections that live longer
          coll4 (make-coll "PROV1" "coll4" 2000)
          coll5 (make-coll "PROV2" "coll5" 2000)
          coll6 (make-coll "PROV2" "coll6" nil) ; no delete time
          all-colls [coll1 coll2 coll3 coll4 coll5 coll6]

          ;; Make a granule for each collection
          gran1 (make-gran coll1 "gran1")
          gran2 (make-gran coll2 "gran2")
          gran3 (make-gran coll3 "gran3")
          gran4 (make-gran coll4 "gran4")
          gran5 (make-gran coll5 "gran5")
          gran6 (make-gran coll6 "gran6")
          all-grans [gran1 gran2 gran3 gran4 gran5 gran6]]
      (index/wait-until-indexed)

      (testing "We can find everything before they expire"
        (is (d/refs-match? all-colls (search/find-refs :collection {})))
        (is (d/refs-match? all-grans (search/find-refs :granule {}))))

      (testing "And after the job runs we can still find everything"
        (ingest/cleanup-expired-collections)
        (index/wait-until-indexed)
        (is (d/refs-match? all-colls (search/find-refs :collection {})))
        (is (d/refs-match? all-grans (search/find-refs :granule {}))))

      (testing "Time can advance part way but the collections still won't be cleaned up"
        (dev-sys-util/advance-time! 99)
        (ingest/cleanup-expired-collections)
        (index/wait-until-indexed)
        (is (d/refs-match? all-colls (search/find-refs :collection {})))
        (is (d/refs-match? all-grans (search/find-refs :granule {}))))

      (testing "collections are removed after their expiration date"
        (dev-sys-util/advance-time! 2)
        (ingest/cleanup-expired-collections)
        (index/wait-until-indexed)
        (is (d/refs-match? [coll4 coll5 coll6] (search/find-refs :collection {})))
        (is (d/refs-match? [gran4 gran5 gran6] (search/find-refs :granule {})))))))



