(ns cmr.system-int-test.ingest.granule-parent-dependency-test
  "CMR granule ingest with validation against parent collection integration tests"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.search-util :as search]
            [clojure.string :as str]
            [cmr.common.mime-types :as mt]
            [cmr.spatial.mbr :as m]
            [cmr.umm.granule :as umm-g]
            [cmr.umm.spatial :as umm-s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.indexer.system :as indexer-system]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

; collection-ref/entry-title
; collection-ref/entry-id
; collection-ref/short-name must match parent collection product/short-name
; collection-ref/version-id must match parent collection product/version-id
; product-specific-attributes/name
; platform-refs/short-name, platform-refs/instrument-refs/short-name
; project-refs - must be a subset of parent collection projects/short-name
; spatial-coverage must match parent collection spatial-coverage/granule-spatial-repsresentation
; temporal - start-date, end-date must be contained in parent-collection start-date, end-date
; two-d-coordinate-system - start-coordinate-1, end-coordinate-1, start-coordinate-2, end-coordinate-2
; must fall within bounds defined in parent collection

(deftest granule-match-parent-collection-test
  (let [psa1 (dc/psa {:name "a-float" :data-type :float :min-value 1.0 :max-value 10.0})
        gpsa (dg/psa "a-float" [7.0])
        i1 (dc/instrument {:short-name "instrument-Sn A"})
        ir1 (dg/instrument-ref {:short-name "instrument-Sn A"})
        p1 (dc/platform {:short-name "platform-Sn A" :instruments [i1]})
        pr1 (dg/platform-ref {:short-name "platform-Sn A" :instrument-refs [ir1]})
        projects (dc/projects "proj")
        mbr1 (umm-s/set-coordinate-system :geodetic (m/mbr 10 10 20 0))
        gran-spatial-rep (apply dg/spatial [mbr1])
        two-d-cs {:name "BRAVO"
                  :coordinate-1 {:min-value 100
                                 :max-value 200}
                  :coordinate-2 {:min-value 300
                                 :max-value 400}}
        g-two-d-cs (dg/two-d-coordinate-system
                     {:name "BRAVO"
                      :start-coordinate-1 110
                      :end-coordinate-1 130
                      :start-coordinate-2 300
                      :end-coordinate-2 328})
        coll1 (dc/collection {:entry-title "short_name_version"
                              :short-name "short_name"
                              :version-id "version"
                              :product-specific-attributes [psa1]
                              :platforms [p1]
                              :organizations [(dc/org :distribution-center "Larc")]
                              :science-keywords [(dc/science-keyword {:category "upcase"
                                                                      :topic "Cool"
                                                                      :term "Mild"})]
                              :projects projects
                              :spatial-coverage (dc/spatial {:gsr :geodetic})
                              :two-d-coordinate-systems [two-d-cs]
                              :related-urls [(dc/related-url {:type "type" :url "htt://www.foo.com"})]
                              :beginning-date-time "1965-12-12T07:00:00.000-05:00"
                              :ending-date-time "1967-12-12T07:00:00.000-05:00"})
        _ (d/ingest "PROV1" coll1 {:format :echo10})
        gran1 (dg/granule coll1 {:platform-refs [pr1]
                                 :spatial-coverage gran-spatial-rep
                                 :two-d-coordinate-system g-two-d-cs
                                 :product-specific-attributes [gpsa]
                                 :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                                 :ending-date-time "1967-10-12T07:00:00.000-05:00"})
        _ (d/ingest "PROV1" gran1)]
    (let [resp (d/ingest "PROV1" coll1 {:format :dif :allow-failure? true})]
      (is (= nil (:errors resp))))
    (let [resp (d/ingest "PROV1" coll1 {:format :dif10 :allow-failure? true})]
      (is (= nil (:errors resp))))
    (let [resp (d/ingest "PROV1" coll1 {:format :iso19115 :allow-failure? true})]
      (is (= nil (:errors resp))))
    (let [resp (d/ingest "PROV1" coll1 {:format :iso-smap :allow-failure? true})]
      (is (= nil (:errors resp))))))

