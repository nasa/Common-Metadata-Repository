(ns cmr.opendap.tests.unit.data.size.granule
  (:require
   [clojure.test :refer :all]
   [cmr.opendap.data.size.granule :as granule]))

(def granule-mb-xml
  (str "<Granule>"
       "  <DataGranule>"
       "    <SizeMBDataGranule>0.186251</SizeMBDataGranule>"
       " </DataGranule>"
       "</Granule>"))

(deftest file-size
  (is (= 195298.328576 (granule/file-size granule-mb-xml))))
