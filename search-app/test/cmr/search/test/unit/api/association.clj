(ns cmr.search.test.unit.api.association
  "Tests to verify functionality in cmr.search.api.association namespace."
  (:require
    [clojure.test :refer [deftest is]]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :as util]
    [cmr.search.api.association :as assoc]))

(deftest association-results->status-code
  (util/are3 [concept-type input return-code]
             (is (= return-code (assoc/association-results->status-code concept-type input)))

             ":service no errors returns 200"
             :service
             '({:service-association {:concept-id "SA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
               {:service-association {:concept-id "SA1200000027-CMR" :revision-id 1} :associated-item {:concept-id "C1200000019-PROV2"}})
             200

             ":service all errors returns 400"
             :service
             '({:errors '("Collection [C100-P5] does not exist or is not visible.") :associated-item {:concept-id "C100-P5"}})
             400

             ":service some error, some succeed returns 207"
             :service
             '({:service-association {:concept-id "SA1200000026-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000013-PROV1"}}
               {:service-association {:concept-id "SA1200000028-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000014-PROV1"}}
               {:errors ["Service [SA1200000015-CMR] and collection [C1200000013-PROV1] can not be associated because the service is already associated with another collection [C1200000012-PROV1]."]
                :associated-item {:concept-id "C1200000013-PROV1"}})
             207

             ":tool no error returns 200"
             :tool
             '({:tool-association {:concept-id "TLA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
               {:tool-association {:concept-id "TLA1200000027-CMR" :revision-id 1} :associated-item {:concept-id "C1200000019-PROV2"}})
             200

             ":tool all errors returns 400"
             :tool
             '({:errors ["TOOL [TLA1200000026-CMR] and collection [C1200000013-PROV1] can not be associated because the tool is already associated with another collection [C1200000012-PROV1]."]
                :associated-item {:concept-id "C1200000013-PROV1"}})
             400

             ":tool some errors returns 207"
             :tool
             '({:tool-association {:concept-id "TLA1200000027-CMR" :revision-id 1} :associated-item {:concept-id "C1200000019-PROV2"}}
               {:errors ["TOOL [TLA1200000026-CMR] and collection [C1200000013-PROV1] can not be associated because the tool is already associated with another collection [C1200000012-PROV1]."]
                :associated-item {:concept-id "C1200000013-PROV1"}})
             207

             ":variable no errors returns 200"
             :variable
             '({:variable-association {:concept-id "VA1200000017-CMR" :revision-id 1} :associated-item {:concept-id "C1200000012-PROV1"}})
             200

             ":variable all errors returns 400"
             :variable
             '({:errors ["Variable [V1200000015-PROV1] and collection [C1200000013-PROV1] can not be associated because the variable is already associated with another collection [C1200000012-PROV1]."]
                :associated-item {:concept-id "C1200000013-PROV1"}})
             400

             "variable some errors returns 207"
             :variable
             '({:errors ["Variable [V1200000015-PROV1] and collection [C1200000013-PROV1] can not be associated because the variable is already associated with another collection [C1200000012-PROV1]."]
                :associated-item {:concept-id "C1200000013-PROV1"}}
               {:variable-association {:concept-id "VA1200000017-CMR" :revision-id 1} :associated-item {:concept-id "C1200000012-PROV1"}}
               {:variable-association {:concept-id "VA1200000018-CMR" :revision-id 1} :associated-item {:concept-id "C1200000012-PROV1"}})
             207

             ":tag no error returns 200 because this func does not dela with :tag associations"
             :tag
             '({:tag-association {:concept-id "TA1200000026-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000013-PROV1"}}
               {:tag-association {:concept-id "TA1200000028-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000014-PROV1"}}
               {:tag-association {:concept-id "TA1200000027-CMR" :revision-id 1} :tagged-item {:concept-id "C1200000015-PROV1"}})
             200

             ":tag returns 200 with error because this func does not deal with :tag associations"
             :tag
             '({:errors ["At least one collection must be provided for tag association."] :status 422})
             200

             "no result returns 200"
             :tag
             '()
              200))

(deftest num-errors-in-result-test
  (util/are3 [input expected]
             (is (= expected (assoc/num-errors-in-assoc-results input)))

             "input has no errors"
             '({:variable-association {:concept-id VA1200000017-CMR, :revision-id 1}, :associated-item {:concept-id C1200000012-PROV1}})
             0

             "input has some errors"
             '({:errors ["Collection [C1200465592-PROV1] does not exist or is not visible."],
                :associated_item {:concept_id "C1200465592-PROV1"}}
               {:variable-association {:concept-id VA1200000017-CMR, :revision-id 1},
                :associated-item {:concept-id "C1200000012-PROV1"}})
             1

             "input has all errors"
             '({:errors ["Collection [C1200465592-PROV1] does not exist or is not visible."],
                :associated_item {:concept_id "C1200465592-PROV1"}}
               {:errors ["Another error message"],
                :associated-item {:concept-id "C1200000012-PROV1"}})
             2))

(deftest api-response-test
  (util/are3 [status-code input expected]
             (let [fun #'assoc/api-response]
               (is (= expected (fun status-code input))))

             "status-code 207 with errors and successes and warnings is given, returns response with separate status codes per association item"
             207
             '({:service-association {:concept-id "SA1200000003-CMR", :revision-id 1}, :associated-item {:concept-id "C1200000001-PROV1"}}
               {:errors ["Collection [C1200465592-PROV1] does not exist or is not visible."], :associated-item {:concept-id "C1200465592-PROV1"}})
             {:status 207
              :body "[{\"service_association\":{\"concept_id\":\"SA1200000003-CMR\",\"revision_id\":1},\"associated_item\":{\"concept_id\":\"C1200000001-PROV1\"},\"status\":200},{\"errors\":[\"Collection [C1200465592-PROV1] does not exist or is not visible.\"],\"associated_item\":{\"concept_id\":\"C1200465592-PROV1\"},\"status\":400}]"
              :headers {"Content-Type" mt/json}}

             "status-code 200 is given, returns data"
             200
             '({:service-association {:concept-id "SA1200000003-CMR", :revision-id 1}, :associated-item {:concept-id "C1200000001-PROV1"}}
               {:service-association {:concept-id "SA1200000004-CMR", :revision-id 2}, :associated-item {:concept-id "C1200000001-PROV1"}})
             {:status 200
              :body "[{\"service_association\":{\"concept_id\":\"SA1200000003-CMR\",\"revision_id\":1},\"associated_item\":{\"concept_id\":\"C1200000001-PROV1\"}},{\"service_association\":{\"concept_id\":\"SA1200000004-CMR\",\"revision_id\":2},\"associated_item\":{\"concept_id\":\"C1200000001-PROV1\"}}]"
              :headers {"Content-Type" mt/json}}

             "status-code 400 is given, returns data"
             400
             '({:errors ["Collection [C1200465592-PROV1] does not exist or is not visible."], :associated-item {:concept-id "C1200465592-PROV1"}})
             {:status 400
              :body "[{\"errors\":[\"Collection [C1200465592-PROV1] does not exist or is not visible.\"],\"associated_item\":{\"concept_id\":\"C1200465592-PROV1\"}}]"
              :headers {"Content-Type" mt/json}}))

(deftest add-individual-statuses-test
  (util/are3 [input expected]
               (is (= expected (assoc/add-individual-statuses input)))

             "data contains mix of errors, successes and warnings, returns various statuses"
             '({:errors ["Collection [C1200465592-PROV1] does not exist or is not visible."], :associated-item {:concept-id "C1200465592-PROV1"}}
               {:service-association {:concept-id "SA1200000003-CMR", :revision-id 1}, :associated-item {:concept-id "C1200000001-PROV1"}}
               {:warning ["Warnings of something"]})
             '({:errors ["Collection [C1200465592-PROV1] does not exist or is not visible."], :associated-item {:concept-id "C1200465592-PROV1"} :status 400}
               {:service-association {:concept-id "SA1200000003-CMR", :revision-id 1}, :associated-item {:concept-id "C1200000001-PROV1"} :status 200}
               {:warning ["Warnings of something"] :status 400})))
