(ns cmr.sizing.tests.unit.granule
  (:require
   [clojure.test :refer :all]
   [cmr.sizing.granule :as granule]))

(def granule-mb-xml
  (str "<Granule>"
       "  <DataGranule>"
       "    <SizeMBDataGranule>0.186251</SizeMBDataGranule>"
       " </DataGranule>"
       "</Granule>"))

(deftest file-size
  (is (= 195298.328576 (granule/file-size granule-mb-xml))))
