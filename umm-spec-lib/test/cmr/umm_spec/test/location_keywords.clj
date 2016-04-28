(ns cmr.umm-spec.test.location-keywords
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.location-keywords :as lk]))

(def sample-keyword-map (list
                         {:category "SPACE", :uuid "3ffa2d97-a066-4b3c-87f9-06779f12e726"}
                         {:category "SPACE", :type "EARTH MAGNETIC FIELD", :subregion-1 "SPACE", :uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}
                         {:category "CONTINENT", :uuid "0a672f19-dad5-4114-819a-2eb55bdbb56a"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :uuid "f2ffbe58-8792-413b-805b-3e1c8de1c6ff"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CHAD", :uuid "9b328d2c-07c9-4fd8-945d-f8d4d12e0bb3"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CAMEROON", :uuid "a028edce-a3d9-4a16-a8c7-d2cb12d3a318"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CENTRAL AFRICAN REPUBLIC", :uuid "065fc27f-5b54-4cfc-a616-8661a55e04b8"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CONGO", :uuid "0ebe4123-f42a-41fb-80ea-bb1b97b9db0b"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CONGO, DEMOCRATIC REPUBLIC", :uuid "3682524d-6ff3-4224-a635-9741726f025f"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :uuid "f2ffbe58-8792-413b-805b-3e1c8de1c6ff"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "ZAIRE", :uuid "7848d9b7-a18d-4519-bec3-b4f5fe19a68f"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "GABON", :uuid "864e3511-3326-4b8f-a534-1a8945fcc3eb"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "EQUATORIAL GUINEA", :uuid "3b515cd8-bc42-4fab-9990-df3be2817938"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "ANGOLA", :uuid "9b0a194d-d617-4fed-9625-df176319892d"}
                         {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "LAKE CHAD", :uuid "a1810ec4-2d03-4d98-b049-2cad380fb789"}))
(comment
 ;;Use for local system testing
(def local-test-system-context
  {:system (get-in user/system [:apps :indexer])})
)

(deftest test-location-keyword-lookup
  (testing "Looking up a root keyword returns the top heirarchy result."
  (let [keyword "CONTINENT"
        expected {:category "CONTINENT", :uuid "0a672f19-dad5-4114-819a-2eb55bdbb56a"}
        actual (lk/find-spatial-keyword sample-keyword-map keyword)]
  (is (= expected actual))))

  (testing "Looking up a uuid returns a valid result"
    (let [uuid "a028edce-a3d9-4a16-a8c7-d2cb12d3a318"
          expected {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CAMEROON", :uuid "a028edce-a3d9-4a16-a8c7-d2cb12d3a318"}
          actual (lk/find-spatial-keyword sample-keyword-map uuid)]
      (is (= expected actual))))

  (testing "Searching for a duplicate keyword retrieves the correct result"
    (let [keyword "SPACE"
          expected "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
          actual (:uuid (lk/find-spatial-keyword sample-keyword-map keyword))]
          (is (= expected actual)))))
