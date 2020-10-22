(ns cmr.search.test.api.association
  "Tests to verify functionality in cmr.search.api.association namespace."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util]
    [cmr.search.api.association :as assoc]))

(deftest association-results->status-code
  (util/are3 [concept-type input return-code]
   (do
     (is (= return-code (assoc/association-results->status-code concept-type input))))

   "no error :service returns 200"
   :service
   '({:service-association {:concept-id "SA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
     {:service-association {:concept-id "SA1200000027-CMR" :revision-id 1} :associated-item {:concept-id "C1200000019-PROV2"}})
   200

   "no error :tool returns 200"
   :tool
   '({:tool-association {:concept-id "TLA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
     {:tool-association {:concept-id "TLA1200000027-CMR" :revision-id 1} :associated-item {:concept-id "C1200000019-PROV2"}})
   200

   "error :service returns 400"
   :service
   '({:errors '("Collection [C100-P5] does not exist or is not visible.") :associated-item {:concept-id "C100-P5"}})
   400

   "no error :variable returns 200"
   :variable
   '({:variable-association {:concept-id "VA1200000017-CMR" :revision-id 1} :associated-item {:concept-id "C1200000012-PROV1"}})
   200

   "error :variable returns 400"
   :variable
   '({:errors ["Variable [V1200000015-PROV1] and collection [C1200000013-PROV1] can not be associated because the variable is already associated with another collection [C1200000012-PROV1]."]
      :associated-item {:concept-id "C1200000013-PROV1"}})
   400

   ":tag no error returns 200"
   :tag
   '({:tag-association {:concept-id "TA1200000026-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000013-PROV1"}}
     {:tag-association {:concept-id "TA1200000028-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000014-PROV1"}}
     {:tag-association {:concept-id "TA1200000027-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000015-PROV1"}})
   200

   ":tag returns 200 even with error"
   :tag
   '({:errors ["At least one collection must be provided for tag association."] :status 422})
   200))
