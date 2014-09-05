(ns cmr.system-int-test.search.granule-orbit-search-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec :as codec]
            [clojure.string :as str]
            [cmr.spatial.dev.viz-helper :as viz-helper]
            [cmr.spatial.serialize :as srl]
            [cmr.common.dev.util :as dev-util]
            [cmr.spatial.lr-binary-search :as lbs]
            [cmr.umm.spatial :as umm-s]
            [cmr.umm.collection :as c]
            [cmr.umm.echo10.collection :as ec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

; (deftest search-by-orbit
;   (let [;; orbit parameters
;         op1 {:swath-width 1450
;              :period 98.88
;              :inclination-angle 98.15
;              :number-of-orbits 0.5
;              :start-circular-latitude -90}
;         op1 {:swath-width 2
;              :period 96.7
;              :inclination-angle 94
;              :number-of-orbits 0.25
;              :start-circular-latitude -50}
;         coll1 (d/ingest "PROV1"
;                              (dc/collection
;                                {:entry-title "orbit-params1"
;                                 :spatial-coverage (dc/spatial :geodetic op1)}))
;         coll2 (d/ingest "PROV1"
;                              (dc/collection
;                                {:entry-title "orbit-params2"
;                                 :spatial-coverage (dc/spatial :geodetic op2)}))
;         g1 (d/ingest "PROV1"
;                      (dc/