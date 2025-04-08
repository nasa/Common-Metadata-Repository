(ns cmr.search.test.unit.api.keyword
  "Tests to verify functionality in cmr.search.api.keyword namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.kms-fetcher :as kms-fetcher]
   [cmr.common.util :as util]
   [cmr.search.api.keyword :as k]
   [cmr.transmit.kms :as kms]))

(deftest parse-hierarchical-keywords
  (util/are2
    [keyword-hierarchy keywords expected-hierarchy]
    (= expected-hierarchy
       (#'cmr.search.api.keyword/flat-keywords->hierarchical-keywords keywords keyword-hierarchy))

    "No matching keywords"
    [:a :b] nil {}

    "Two keys"
    [:a :b]
    [{:a "A" :b "B"}]
    {:a #{{:value "A"
           :subfields #{:b}
           :b #{{:value "B"}}}}}

    "Missing one key"
    [:a :b :c]
    [{:a "A" :c "C"}]
    {:a #{{:value "A"
           :subfields #{:c}
           :c #{{:value "C"}}}}}

    "Missing several keys in a row"
    [:a :b :c :d :e :f :g :h]
    [{:a "A" :d "D" :h "H"}]
    {:a #{{:value "A"
           :subfields #{:d}
           :d #{{:value "D"
                 :subfields #{:h}
                 :h #{{:value "H"}}}}}}}

    "No missing keys with multiple keywords"
    [:a :b :c]
    [{:a "A1" :b "B1" :c "C1"}
     {:a "A1" :b "B1" :c "C2"}
     {:a "A2" :b "B2" :c "C3"}]
    {:a #{{:value "A1"
           :subfields #{:b}
           :b #{{:value "B1"
                 :subfields #{:c}
                 :c #{{:value "C1"}
                      {:value "C2"}}}}}
          {:value "A2"
           :subfields #{:b}
           :b #{{:value "B2"
                 :subfields #{:c}
                 :c #{{:value "C3"}}}}}}}

    "Multiple subfields"
    [:a :b :c]
    [{:a "A1" :b "B1"}
     {:a "A1" :c "C1"}]
    {:a #{{:value "A1"
           :subfields #{:b :c}
           :b #{{:value "B1"}}
           :c #{{:value "C1"}}}}}

    "Root field in hierarchy missing"
    [:a :b :c :d] [{:b "B"}] {:b #{{:value "B"}}}

    "Basic UUID test"
    [:a :b]
    [{:a "A" :b "B" :uuid "abc"}]
    {:a #{{:value "A"
           :subfields #{:b}
           :b #{{:value "B"
                 :uuid "abc"}}}}}

    "UUID Test where everything is a leaf"
    [:a :b :c]
    [{:a "A" :uuid "a123"}
     {:a "A" :b "B" :uuid "b123"}
     {:a "A" :b "B" :c "C" :uuid "c123"}]
    {:a #{{:value "A"
           :uuid "a123"
           :subfields #{:b}
           :b #{{:value "B"
                 :uuid "b123"
                 :subfields #{:c}
                 :c #{{:value "C"
                       :uuid "c123"}}}}}}}

    "Complex UUID test"
    [:a :b :c :d]
    [{:a "A1" :c "C1" :uuid "a1c1"}
     {:a "A1" :c "C1" :d "D1" :uuid "a1c1d1"}
     {:a "A2" :uuid "a2"}
     {:a "A2" :d "D1" :uuid "a2d1"}
     {:a "A3" :b "B1" :c "C1" :d "D1" :uuid "a3b1c1d1"}
     {:a "A3" :c "C2" :uuid "a3c2"}
     {:a "A3" :d "D2" :uuid "a3d2"}]
    {:a #{{:value "A1"
           :subfields #{:c}
           :c #{{:value "C1"
                 :uuid "a1c1"
                 :subfields #{:d}
                 :d #{{:value "D1"
                       :uuid "a1c1d1"}}}}}
          {:value "A2"
           :uuid "a2"
           :subfields #{:d}
           :d #{{:value "D1"
                 :uuid "a2d1"}}}
          {:value "A3"
           :subfields #{:b :c :d}
           :b #{{:value "B1"
                 :subfields #{:c}
                 :c #{{:value "C1"
                       :subfields #{:d}
                       :d #{{:value "D1"
                             :uuid "a3b1c1d1"}}}}}}
           :c #{{:value "C2"
                 :uuid "a3c2"}}
           :d #{{:value "D2"
                 :uuid "a3d2"}}}}}))

;;TODO: we should drop support :spatial-keywords-old as this effort should be done
(deftest make-sure-nested-fields-mappings-exist
  (testing "Making sure the nested-fields-mapping list contains the new valid keywords"
    (let [valid-keywords (remove #{:spatial-keywords-old}
                                 (concat (keys kms/keyword-scheme->field-names)
                                         (keys kms/cmr-to-gcmd-keyword-scheme-aliases)))]
      (is
        (every?
           #(some? ((kms/translate-keyword-scheme-to-cmr %) kms-fetcher/nested-fields-mappings))
           valid-keywords)))))
