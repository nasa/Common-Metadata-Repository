(ns cmr.ingest.test.services.jobs
  "This tests some of the more complicated functions of cmr.ingest.services.jobs"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as u :refer [are3]]
    [cmr.ingest.services.jobs :as jobs]))

(deftest create-query-params 
  (is (= {"polygon" "-78,-18,-77,-22,-73,-16,-74,-13,-78,-18"
          "concept-id" "G123-PROV1"} 
         (#'jobs/create-query-params "polygon=-78,-18,-77,-22,-73,-16,-74,-13,-78,-18&concept-id=G123-PROV1"))))
