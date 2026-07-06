(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.quality
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.quality :as quality :refer [parse-quality]]))

(def text-xpath "/report/DQ_QuantitativeAttributeAccuracy/evaluationMethodDescription")

(deftest parse-quality-test
  (testing "1. Returns nil immediately if the xpath cannot find a quality string or string is empty"
    (let [doc "<report></report>"]
      (is (nil? (parse-quality doc text-xpath true))))
    (let [doc "<report><DQ_QuantitativeAttributeAccuracy><evaluationMethodDescription><CharacterString></CharacterString></evaluationMethodDescription></DQ_QuantitativeAttributeAccuracy></report>"]
      (is (nil? (parse-quality doc text-xpath true)))))

  (testing "2. Correctly reconstructs UMM map structure when all subfields are present"
    (let [doc (str "<report><DQ_QuantitativeAttributeAccuracy><evaluationMethodDescription><CharacterString>"
                   "Summary: Overview text. Strengths: High accuracy. Limitations: 250m grid blur. KnownIssues: Snow confusion. Other: Grid flags."
                   "</CharacterString></evaluationMethodDescription></DQ_QuantitativeAttributeAccuracy></report>")
          expected {:Summary "Overview text."
                    :QualityContentDetails {:Strengths "High accuracy."
                                            :Limitations "250m grid blur."
                                            :KnownIssues "Snow confusion."
                                            :Other "Grid flags."}}]
      (is (= expected (parse-quality doc text-xpath true)))))

  (testing "3. Falls back to using the entire raw string as Summary if no structured prefixes match"
    (let [doc (str "<report><DQ_QuantitativeAttributeAccuracy><evaluationMethodDescription><CharacterString>"
                   "This is just a plain legacy quality text block with no special formatting headers at all."
                   "</CharacterString></evaluationMethodDescription></DQ_QuantitativeAttributeAccuracy></report>")
          expected {:Summary "This is just a plain legacy quality text block with no special formatting headers at all."}]
      (is (= expected (parse-quality doc text-xpath true)))))

  (testing "4. Cleanly omits QualityContentDetails if no details keys are found or matched"
    (let [doc (str "<report><DQ_QuantitativeAttributeAccuracy><evaluationMethodDescription><CharacterString>"
                   "Summary: Just a summary text chunk."
                   "</CharacterString></evaluationMethodDescription></DQ_QuantitativeAttributeAccuracy></report>")
          expected {:Summary "Just a summary text chunk."}]
      (is (= expected (parse-quality doc text-xpath true)))))

  (testing "5. Safely filters out subfields that resolve to empty strings"
    (let [doc (str "<report><DQ_QuantitativeAttributeAccuracy><evaluationMethodDescription><CharacterString>"
                   "Summary: Overview Strengths: Good data Limitations: "
                   "</CharacterString></evaluationMethodDescription></DQ_QuantitativeAttributeAccuracy></report>")
          expected {:Summary "Overview"
                    :QualityContentDetails {:Strengths "Good data"}}]
      (is (= expected (parse-quality doc text-xpath true))))))
