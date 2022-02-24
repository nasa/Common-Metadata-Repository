(ns cmr.common.test.util
  (:require
   [clj-time.core :as t]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   ; [clojure.test.check.clojure-test :refer [defspec]]
   ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
   [cmr.common.test.test-check-ext :as gen-ext :refer [defspec]]
   [cmr.common.util :as util :refer [defn-timed]]))

(deftest test-sequence->fn
  (testing "vector of values"
    (let [f (util/sequence->fn [1 2 3])]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "list of values"
    (let [f (util/sequence->fn '(1 2 3))]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= nil (f)))))
  (testing "empty vector"
    (let [f (util/sequence->fn [])]
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "empty list"
    (let [f (util/sequence->fn '())]
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "infinite sequence of values"
    (let [f (util/sequence->fn (iterate inc 1))]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= 4 (f)))
      (is (= 5 (f))))))

(deftest are2-test
  (testing "Normal use"
    (util/are2
      [x y] (= x y)
      "The most basic case with 1"
      2 (+ 1 1)
      "A more complicated test"
      4 (* 2 2))))

(deftest are3-test
  (testing "Normal use"
    (util/are3
      [x y] (is (= x y))
      "The most basic case with 1"
      2 (+ 1 1)
      "A more complicated test"
      4 (* 2 2))))

(defn-timed test-timed-multi-arity
  "The doc string"
  ([f]
   (test-timed-multi-arity f f))
  ([fa fb]
   (test-timed-multi-arity fa fb fa))
  ([fa fb fc]
   (fa)
   (fb)
   (fc)))

(defn-timed test-timed-single-arity
  "the doc string"
  [f]
  (f)
  (f)
  (f))

(deftest defn-timed-test
  (testing "single arity"
    (let [counter (atom 0)
          counter-fn #(swap! counter inc)]
      (is (= 3 (test-timed-single-arity counter-fn))))
    (testing "multi arity"
      (let [counter (atom 0)
            counter-fn #(swap! counter inc)]
        (is (= 3 (test-timed-multi-arity counter-fn)))))))

(deftest build-validator-test
  (let [error-type :not-found
        errors ["error 1" "error 2"]
        validation (fn [a b]
                     (cond
                       (> a b) errors
                       (< a b) []
                       :else nil))
        validator (util/build-validator error-type validation)]

    (is (nil? (validator 1 1)) "No error should be thrown for valid returning nil")
    (is (nil? (validator 0 1)) "No error should be thrown for valid returning empty vector")

    (try
      (validator 1 0)
      (is false "An exception should have been thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type error-type
                :errors errors}
               (ex-data e)))))))

(deftest remove-nil-keys-test
  (is (= {:a true :c "value" :d false :e "" :f " "}
         (util/remove-nil-keys
          {:a true :b nil :c "value" :d false :e "" :f " "})))
  ;; remove-nil-keys will not go down to the enclosed maps
  (is (= {:a true :c {:d false :e nil :f " "} :g "value"}
         (util/remove-nil-keys
          {:a true :b nil :c {:d false :e nil :f " "} :g "value"}))))

(deftest scrub-token-test
  (let [token-with-at-least-15-chars "1234567890abcdefg"
        token-with-at-least-5-chars "1234567890"
        token-with-less-than-5-chars "1234"]
   (is (= "12345XXXcdefg"
          (util/scrub-token token-with-at-least-15-chars)))
   (is (= "12345XXX"
          (util/scrub-token token-with-at-least-5-chars)))
   (is (= "XXX"
          (util/scrub-token token-with-less-than-5-chars)))))

(deftest remove-map-keys-test
  (is (= {:a true :c "value" :d false}
         (util/remove-map-keys
           (fn [v] (or (nil? v) (and (string? v) (str/blank? v))))
           {:a true :b nil :c "value" :d false :e "" :f " "}))))

(deftest remove-empty-maps-test
  (are [x y]
    (= x (util/remove-empty-maps y))
    nil {:x {:y {:z nil :foo nil}}}
    {:x {:y {:z 1}}} {:x {:y {:z 1 :a {:b nil}}}}
    [{:x 1} {:y 2}] [{} {:x 1} {:a nil :b {:c nil}} {:y 2}]))

(deftest remove-nils-empty-maps-seqs
  (testing "Remove nils, empty maps, emtpy vectors, and empty sequences test"
    (is (= {:c {:y 5 :m ["1" "2"] :z 4 :t [{:e "1"} {:h "50"}] :u '(1 "2")} :b 1}
           (util/remove-nils-empty-maps-seqs {:a nil
                                              :b 1
                                              :c {:z 4
                                                  :y 5
                                                  :x nil
                                                  :w []
                                                  :v '()
                                                  :u '(1 "2")
                                                  :t [{:e "1" :f nil} {:g nil :h "50"}]
                                                  :m ["1" "2"]
                                                  :s {}}
                                              :d {:q nil :r nil}})))))

(deftest rename-keys-with-test
  (testing "basic rename key tests"
    (let [params {:k [1 2]}
          param-aliases {:k :replaced-k}
          merge-fn concat
          expected {:replaced-k [1 2]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo 2 :bar 4}
          param-aliases {:bar :foo}
          merge-fn +
          expected {:foo 6}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo 5}
          param-aliases {:bar :foo}
          merge-fn +]
      (is (= params
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo [1 2] :bar [3 4]}
          param-aliases {:bar :foo}
          merge-fn concat
          expected {:foo [1 2 3 4]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo [1 2] :bar [3 4]}
          param-aliases {:bar :foo :x :foo}
          merge-fn concat
          expected {:foo [1 2 3 4]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn)))))
  (testing "multiples keys aliasing to same key tests"
    (let [params {:foo 2 :bar 4 :k1 8 :k3 16}
          param-aliases {:bar :foo :k1 :foo :k2 :foo}
          merge-fn +
          expected {:foo 14 :k3 16}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))

    (let [merge-fn #(set/union (if (set? %1) %1 #{%1})
                               (if (set? %2) %2 #{%2}))]
      (let [params {:foo #{1 2} :bar #{3 4} :k1 8 :k2 "d"}
            param-aliases {:bar :foo :k1 :foo :k2 :foo}
            expected {:foo #{1 2 "d" 8 3 4}}]
        (is (= expected
               (util/rename-keys-with params param-aliases merge-fn))))

      (let [params {:concept-id #{"G9000000009-PROV2"},
                    :echo-granule-id #{"G1000000006-PROV2"}
                    :echo-collection-id "C1000000002-PROV2"}
            param-aliases {:echo-granule-id :concept-id :echo-collection-id :concept-id :dummy-key :replace-key}
            expected {:concept-id #{"G9000000009-PROV2" "C1000000002-PROV2" "G1000000006-PROV2"}}]
        (is (= expected
               (util/rename-keys-with params param-aliases merge-fn)))))))


(deftest any-test
  (are [expected f items]
       (is (= expected (util/any-true? f items)))

      false (constantly true) nil
      false (constantly true) []
      false even? [1]
      false even? [1 3 5 7]

      true even? [2]
      true even? [1 2]
      true even? [2 1 3 7]

      ;; It should return true before it checks everything. The symbol at the end will trigger an exception
      true #(> (/ 20 %) 2) [20.0 1.0 :not-a-number]))

(defspec map-n-spec 1000
  (for-all [n gen/s-pos-int
            step gen/s-pos-int
            items (gen/vector gen/int)]
    ;; Verifies map-n is equivalent to partition
    (= (util/map-n identity n step items)
       (map identity (partition n step items)))))

(defspec map-n-all-spec 1000
  (for-all [n gen/s-pos-int
            step gen/s-pos-int
            items (gen/vector gen/int)]
    ;; Verifies map-n-all is equivalent to partition-all
    (= (util/map-n-all identity n step items)
       (map identity (partition-all n step items)))))

(defspec pmap-n-all-spec 1000
  (for-all [n gen/s-pos-int
            items (gen/vector gen/int)]
    ;; Verifies pmap-n is equivalent to map-n (just runs in parallel)
    (= (util/map-n-all identity n items)
       (util/pmap-n-all identity n items))))

(defspec double->string-test 1000
  (for-all [d (gen/fmap double gen/ratio)]
    (let [^String double-str (util/double->string d)
          parsed (Double. double-str)]
      ;; Check it should contain an exponent and it doesn't lose precision.
      (and (not (re-find #"[eE]" double-str))
           (= parsed d)))))

(deftest binary-search-test
  (testing "along number line for integer"
    (let [range-size 100
          find-value 23
          matches-fn (fn [^long v minv maxv ^long depth]
                       (if (> depth range-size)
                         (throw (Exception. (format "Depth [%d] exceeded max [%d]" depth range-size)))
                         (cond
                           (< v find-value) :less-than
                           (> v find-value) :greater-than
                           :else v)))
          middle-fn #(int (/ (+ ^long %1 ^long %2) 2))]
      (is (= find-value (util/binary-search 0 range-size middle-fn matches-fn))))))

(deftest greater-than?-less-than?-test
  (testing "greater-than? and less-than?"
    (are [items]
         (and
           (apply util/greater-than? items)
           (apply util/less-than? (reverse items)))
         []
         [3]
         [3 2]
         [nil]
         [-1 nil]
         [3 2 1]
         [3.0 2.0 1.0 0.0]
         ["c" "b" "a"]
         [:d :c :b :a]
         [(t/date-time 2015 1 14 4 3 27) (t/date-time 1986 10 14 4 3 28)])))

(deftest get-keys-in-test
  (util/are2
    [map-or-coll expected]
    (= expected (util/get-keys-in map-or-coll))

    "Simple map"
    {:a "A" :b "B"}
    #{:a :b}

    "Simple collection"
    [{:a "A" :b "B"}
     {:a "C" :d "D"}]
    #{:a :b :d}

    "Nested map"
    {:a {:b "B"
         :c "C"}
     :b {:d "D"}
     :c [{:e "E"
          :f "F"}
         [{:g "G"
           :h "H"}]]}
    #{:a :b :c :d :e :f :g :h}

    "Nested collection"
    [[{:a [{:b "B"
            :c "C"}
           {:d "D"
            :e "E"}]}
      {:f "F"}]
     {:g [{:h "H"}]}]
    #{:a :b :c :d :e :f :g :h}

    "Complex keys in map"
    {{:a "a"} "map"
     (symbol "a+-*&%$#!") "symbol"
     1 2
     "str" [{1 2
             3 4}]
     #{5 6 7} "set"
     [8 9] "vec"}
    #{{:a "a"} (symbol "a+-*&%$#!") 1 "str" 3 #{5 6 7} [8 9]}

    "Empty map"
    {}
    #{}

    "Empty collection"
    []
    #{}))

(deftest map->path-values-test
  (util/are2
    [test-map expected]
    (= expected (util/map->path-values test-map))

    "Simple map"
    {:a 1 :b 2}
    {[:a] 1 [:b] 2}

    "Nested map, paths into vectors aren't currently supported."
    {:a {:b "B"
         :c "C"}
     :b {:d "D"}
     :c [{:e "E":f "F"} [{:g "G":h "H"}]]}
    {[:a :b] "B"
     [:a :c] "C"
     [:b :d] "D"
     [:c] [{:e "E", :f "F"} [{:g "G", :h "H"}]]}

    "Complex keys in map"
    {{:a "a"} "map"
     (symbol "a+-*&%$#!") "symbol"
     1 2
     "str" [{1 2
             3 4}]
     #{5 6 7} "set"
     [8 9] "vec"}
    {[{:a "a"}] "map"
     [(symbol "a+-*&%$#!")] "symbol"
     [1] 2
     ["str"] [{1 2, 3 4}]
     [#{7 6 5}] "set"
     [[8 9]] "vec"}

    "Empty map"
    {}
    {}))

(deftest map-matches-path-values-test
  (util/are2
    [path-values test-map]
    (util/map-matches-path-values? path-values test-map)

    "Simple map"
    {[:a] 1 [:b] 2}
    {:a 1 :b 2}

    "partial path-values map"
    {[:b] 2}
    {:a 1 :b 2}

    "Nested map"
    {[:a :b] "B"
     [:a :c] "C"
     [:b :d] "D"
     [:c] [[{:e "E", :f "F"} [{:g "G", :h "H"}]]]}
    {:a {:b "B"
         :c "C"}
     :b {:d "D"}
     :c [{:e "E":f "F"} [{:g "G":h "H"}]]}

    "Complex keys in map"
    {[{:a "a"}] "map"
     [(symbol "a+-*&%$#!")] "symbol"
     [1] 2
     ["str"] [[{1 2, 3 4}]]
     [#{7 6 5}] "set"
     [[8 9]] "vec"}
    {{:a "a"} "map"
     (symbol "a+-*&%$#!") "symbol"
     1 2
     "str" [{1 2
             3 4}]
     #{5 6 7} "set"
     [8 9] "vec"}

    "Empty map"
    {}
    {})
  (testing "not match"
    (is (not (util/map-matches-path-values? {[:a] 2} {:a 1 :b 2})))))

(deftest filter-matching-maps-test
  (let [nested-map {:a {:b "B"
                        :c "C"}
                    :b {"d" "D"}
                    :c [{:e "E":f "F"} [{:g "G":h "H"}]]}
        type-map {[{:a "a"}] "map"
                  [(symbol "a+-*&%$#!")] "symbol"
                  [1] 2
                  ["str"] [{1 2, 3 4}]
                  [#{7 6 5}] "set"
                  [[8 9]] "vec"}
        maps [nested-map
              type-map
              {:c [{:e "E"}]}
              {:a {:b "B"}}
              {:x 1}
              {:j "j"}
              {:j "jj"}
              {:j "jjj"}
              {:k {:l "L"
                   :m "M"}}]]
    (util/are2
      [matching-map matched]
      (= matched (util/filter-matching-maps matching-map maps))

      "simple match"
      {:x 1}
      [{:x 1}]

      "nested all match"
      ;;vectors are treated as OR lists, not matching values, so nest within a top level vector
      (assoc nested-map :c [(:c nested-map)])
      [nested-map]

      "nested partial match"
      {:a {:c "C"}}
      [nested-map]

      "multiple matches"
      {:a {:b "B"}}
      [nested-map {:a {:b "B"}}]

      "value is matched exactly"
      {:k {:l "L" :m "M"}}
      [{:k {:l "L" :m "M"}}]

      "when value is a vector, it is not matched exactly"
      {:c [{:e "E"}]}
      []

      "vector value is only matched when nested in a vactor (treated as a OR list)"
      {:c [[{:e "E"}]]}
      [{:c [{:e "E"}]}]

      "sequential values are ORed"
      {:j ["j" "jjj"]}
      [{:j "j"}
       {:j "jjj"}]

      "type match"
      ;;vectors are treated as OR lists, not matching values, so nest within a top level vector
      (assoc type-map ["str"] [(get type-map ["str"])])
      [type-map]

      "Empty matching map finds everything"
      {}
      maps)))

(deftest lazy-assoc-and-get-test
  (let [call-count (atom 0)
        my-map (-> {}
                   (util/lazy-assoc :a (swap! call-count inc))
                   (assoc :a 3))]
    (testing "value is not evaluated when it is assoc'd"
      (is (= 0 @call-count)))

    (testing "lazy get value"
      (is (= 1 (util/lazy-get my-map :a))))

    (testing "lazy get value is the same multiple times"
      (is (= 1 (util/lazy-get my-map :a))))

    (testing "value is only evaluated once"
      (is (= 1 @call-count)))

    (testing "original key is not used"
      (is (= 3 (get my-map :a))))))

(deftest extract-between-strings-test
  (let [test-str "abcdefghjklmnopqrstuvwxyz"]
    (are [expected start end include-start-and-end?]
         (= expected (util/extract-between-strings test-str start end include-start-and-end?))
         test-str "a" "z"  true
         test-str "ab" "xyz" true
         "abcdef" "ab" "f" true
         "abcdef" "ab" "cdef" true
         "tuvwxyz" "tu" "wxyz" true
         "fghjklmno" "fgh" "lmno" true

         "bcdefghjklmnopqrstuvwxy" "a" "z"  false
         "cdefghjklmnopqrstuvw" "ab" "xyz" false
         "cde" "ab" "f" false
         ;; When there's no data between start and end nil is returned
         nil "ab" "cdef" false
         "v" "tu" "wxyz" false
         "jk" "fgh" "lmno" false

         ;; not found cases
         nil "z" "a" true
         nil "z" "z" true
         nil "a" "a" true
         nil "g" "f" true
         nil "acd" "z" true
         nil "abc" "zyx" true)))

(defspec lz4-compression 100
  (for-all [s gen/string]
    (= s (-> s util/string->lz4-bytes util/lz4-bytes->string))))

(defspec gzip-base64-encode 100
  (for-all [s gen/string]
    (= s (-> s util/string->gzip-base64 util/gzip-base64->string))))

(deftest truncate-nils
  (is (= '(1 2 nil 3 false)
         (util/truncate-nils [1 2 nil 3 false nil nil nil]))))

(deftest map-longest
  (is (= '(3 6 9 12 10 6 7)
         (util/map-longest +
                           0
                           [1 2 3 4]
                           [1 2 3 4 5]
                           [1 2 3 4 5 6 7]))))

(deftest key-sorted-map
  (let [key-order [:c :d :f :a]
        m (util/key-sorted-map key-order)]

    (testing "initial state"
      (is (= m {})))

    (testing "known key order"
      (is (= key-order (keys (into m (zipmap key-order (repeat nil)))))))

    (testing "unknown keys"
      (is (= [:x :y :z]  (keys (into m {:z nil :y nil :x nil})))))

    (testing "known and unknown keys"
      (is (= [:c :d :f :a ;; known before unknown
              :x :y :z]
             (keys (into m {:z nil :y nil :x nil
                            :a nil :d nil :c nil :f nil})))))))

(deftest update-in-all
  (util/are3
   [args result]
   (let [[obj path value] args]
     (is (= result
            (util/update-in-all obj path (fn[_] value)))))

   "nil values in the path"
   [{:a nil :b :d} [:a :b] 0]
   {:a nil :b :d}

   "sequential values in the path"
   [{:a [{:b 1 :c 2} {:b 3 :c 4}] :d 5} [:a :b] 0]
   {:a [{:b 0 :c 2} {:b 0 :c 4}] :d 5}

   "hash values in the path"
   [{:a 1 :b 2} [:a] 0]
   {:a 0 :b 2}

   "nested hash values in the path"
   [{:a {:b {:c 1 :d 2} :e 3} :f 4} [:a :b :c] 0]
   {:a {:b {:c 0 :d 2} :e 3} :f 4}

   "nested sequential values in the path"
   [{:a [{:b [{:c 1 :d 2}]}
         {}
         {:b [{:c 3 :d 4} {:c 5}]}
         {:b [{:d 6} {:d 7}]}]}
    [:a :b :c]
    0]
   {:a [{:b [{:c 0 :d 2}]}
        {}
        {:b [{:c 0 :d 4} {:c 0}]}
        {:b [{:d 6} {:d 7}]}]}))

(deftest get-in-all
  (util/are3
   [args result]
   (is (= result (apply util/get-in-all args)))

   "nil values in the path"
   [{:a nil :b :c} [:a :b]]
   []

   "sequential values in the path"
   [{:a [{:b 1 :c 2} {:b 3 :c 4}] :d 5} [:a :b]]
   [1 3]

   "hash values in the path"
   [{:a 1 :b 2} [:a]]
   [1]

   "nested hash values in the path"
   [{:a {:b {:c 1 :d 2} :e 3} :f 4} [:a :b :c]]
   [1]

   "nested sequential values in the path"
   [{:a [{:b [{:c 1 :d 2}]}
         {}
         {:b [{:c 3 :d 4} {:c 5}]}
         {:b [{:d 6} {:d 7}]}]}
    [:a :b :c]]
   [1 3 5]

   "sequential values at the end of the path"
   [{:a [{:b [{:c [1 2 3] :d 2}]}
         {}
         {:b [{:c [4 5] :d 4} {:c [6]}]}
         {:b [{:d 6} {:d 7}]}]}
    [:a :b :c]]
   [[1 2 3] [4 5] [6]]))

(deftest compare-natural-strings
  (testing "natural string sort"
    (is (=
         [""
          "1abc2"
          "2abc2"
          "10abc2"
          "ab10"
          "abc"
          "abc1"
          "abc1abc 0"
          "abc01abc 1"
          "abc1abc 2"
          "Abc1abc 3"
          "Abc1abc 10"
          "abc2"
          "abc10"
          "abc10a"]
         (sort util/compare-natural-strings
               [""
                "abc"
                "abc1"
                "abc2"
                "ab10"
                "abc10a"
                "abc10"
                "1abc2"
                "2abc2"
                "10abc2"
                "abc1abc 2"
                "abc01abc 1"
                "Abc1abc 10"
                "Abc1abc 3"
                "abc1abc 0"])))))

(deftest compare-vectors
  (testing "vector sort"
    (is (=
         [[]
          [1 2]
          [1 2 2]
          [1 2 2 4]
          [1 2 3]
          [2 1]]
         (sort util/compare-vectors
               [[1 2 3]
                [1 2 2]
                [1 2 2 4]
                []
                [1 2]
                [2 1]])))))

(deftest xor-test
  (is (false? (util/xor)))
  (is (false? (util/xor false)))
  (is (false? (util/xor false nil)))
  (is (false? (util/xor false false false)))
  (is (true? (util/xor true)))
  (is (true? (util/xor true false)))
  (is (true? (util/xor nil nil 1 nil)))
  (is (true? (util/xor false true false))))

(deftest doall-recursive-test
  (let [marker1 (atom false)
        marker2 (atom false)
        marker3 (atom false)
        ;; Takes an atom and returns a function that when called will set the marker to true.
        mark-realized (fn [marker]
                        (fn [v]
                          (reset! marker true)
                          v))
        ;; The map calls here are all lazy. The recursive doall should force them all to evaluate
        ;; and trigger all of our atoms to be set to true
        v (map
           (mark-realized marker1)
           [1
            ;; The contents of the map would not be realized by a regular doall.
            {:a (map (mark-realized marker2) [1 2])
             :b {:foo [(map (mark-realized marker3) [1 2 3])]}}
            3])]
    (is (not @marker1))
    (is (not @marker2))
    (is (not @marker3))
    (let [result (util/doall-recursive v)]
      (is @marker1)
      (is @marker2)
      (is @marker3)
      (is (= [1 {:a [1 2] :b {:foo [[1 2 3]]}} 3]
             result)))))

;; Test fast-map exception handling. In pmap the error thrown will be a
;; java.util.concurrent.ExecutionException which will mask the original exception. Fast-map
;; throws the original exception.
(deftest fast-map
  (testing "Basic fast-map test"
    (is (= [2 3 4] (util/fast-map inc [1 2 3]))))
  (testing "Fast-map exception handling"
    (is (thrown? NullPointerException
          (try
            (util/fast-map inc [1 nil 3]) ; Will throw NullPointerException
            ;; If we get to this point, the test failed because the exception was not thrown correctly
            (is false "Fast-map did not throw an exception as expected")
            (catch java.util.concurrent.ExecutionException ee
              ; We don't want an ExecutionException, we want to get the null pointer exception
              (is false "Fast-map threw ExecutionException and should throw NullPointerException")))))))

(deftest max-compare-test
  (util/are3
    [coll expected-max]
    (is (= expected-max (apply util/max-compare coll)))

    "Numbers"
    [1 7 52 3] 52

    "Large numbers"
    [123456789 14 3 456789123] 456789123

    "Joda times"
    [(t/date-time 2006 10 13 23) (t/date-time 2006 10 14 22) (t/date-time 1986 12 15 14)]
    (t/date-time 2006 10 14 22)

    "Empty collection is ok"
    [] nil

    "Only nil is ok"
    [nil] nil

    "Some nils"
    [1 nil 15 nil 32 nil 14] 32

    "Strings - notice lowercase are considered greater than uppercase characters"
    ["Apple" "Orange" "banana" "dog" "DANGER"] "dog"

    "Arrays - maximum length of the array is considered the greatest"
    [[51 52 435] [7] [1 2 3 4]] [1 2 3 4]

    "Set of numbers"
    #{1 7 52 3} 52))

(deftest safe-lower-upper-case-test
  (testing "safe-lowercase"
    (is (= (util/safe-lowercase false) (str/lower-case false)))
    (is (= (util/safe-lowercase true) (str/lower-case true)))
    (is (= (util/safe-lowercase "StRing") (str/lower-case "StRing")))
    (is (= (util/safe-lowercase nil) nil)))
  (testing "safe-upperrcase"
    (is (= (util/safe-uppercase false) (str/upper-case false)))
    (is (= (util/safe-uppercase true) (str/upper-case true)))
    (is (= (util/safe-uppercase "StRing") (str/upper-case "StRing")))
    (is (= (util/safe-uppercase nil) nil))))

(deftest human-join
  (testing "one element"
    (is (= (util/human-join ["one"] "," "and") "one")))
  (testing "two elements"
    (is (= (util/human-join ["one" "two"] "," "and") "one and two")))
  (testing "three elements"
    (is (= (util/human-join["one" "two" "three"] "," "and") "one, two, and three")))
  (testing "four elements"
    (is (= (util/human-join ["one" "two" "three" "four"] "," "and") "one, two, three, and four"))))

(def update-in-each-vector-test-data
  {:a {:b [{:c "description"
            :d "DataContactURL"}
           {:c "description2"
            :d "DistributionURL"}
           {:c "description3"
            :d "DistributionURL"}]}})

(deftest update-in-each-vector
  (testing "update-in-each-vector"
    (is (= {:a {:b '({:c "description"}
                     {:c "description2"}
                     {:c "description3"})}}
           (util/update-in-each update-in-each-vector-test-data [:a :b] dissoc :d)))
    (is (= {:a {:b [{:c "description"}
                    {:c "description2"}
                    {:c "description3"}]}}
           (util/update-in-each-vector update-in-each-vector-test-data [:a :b] dissoc :d)))))

(deftest convert-resolution-to-meters-test
  "Test the conversion of resolution values to meters."

  (util/are3 [expected test-value test-unit]
    (is (= expected
           (util/convert-to-meters test-value test-unit)))

    "Test Meter conversion"
    3
    3
    "Meters"

    "Test Decimal Degree conversion"
    111195
    1
    "Decimal Degrees"

    "Test Kilometer conversion"
    1000
    1
    "Kilometers"

    "Test Statue Mile conversion"
    3218.688
    2
    "Statute Miles"

    "Test nautical Mile conversion"
    3704.002
    2
    "Nautical Miles"

    "Test if the value is zero"
    0
    0
    "Decimal Degrees"

    "Test if the unit is nil.
     Although since the unit is required and controlled this shouldn't happen."
    3
    3
    nil

    "Test if the value is nil"
    nil
    nil
    nil))

(deftest remove-nil-tail-test
  (util/are3 [input expected]
    (is (= expected (util/remove-nil-tail input)))

    "No nils"
    [:a :b :c] [:a :b :c]

    "Trailing nils"
    [:a :b :c nil nil nil] [:a :b :c]

    "Mixed nils"
    [:a nil :b nil :c nil nil nil] [:a nil :b nil :c]

    "Leading nils"
    [nil :a :b] [nil :a :b]

    "All nils"
    [nil nil nil] []

    "Single nil"
    [nil] []

    "Empty collection"
    [] []

    "List"
    '(:a nil :b nil :c nil nil) '(:a nil :b nil :c)

    "nil as input"
    nil nil))
