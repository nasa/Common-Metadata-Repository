(ns cmr.common.test.xml
  (:require [clojure.test :refer :all]
            [cmr.common.xml :as cx]
            [clojure.data.xml :as x]
            [clj-time.core :as t]))


(deftest remove-xml-processing-instructions-test
  (let [expected "<foo><bar>&lt;!DOCTYPE ?xml?&gt;</bar></foo>" ; contains some things that should be left alone
        with-processing-ins
        (str
          "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
          "<!DOCTYPE NAME SYSTEM \"names.dtd\">"
          expected)]
    (is (= expected (cx/remove-xml-processing-instructions with-processing-ins)))))


(def sample-xml
  "<top>
  <inner ia=\"1\" ib=\"foo\">
  <alpha>45</alpha>
  <bravo>ovarb</bravo>
  <trueflag>true</trueflag>
  <falseflag>false</falseflag>
  <datetime>1986-10-14T04:03:27.456Z</datetime>
  </inner>
  </top>")

(def parsed-sample-xml
  (x/parse-str sample-xml))

(deftest element-at-path-test
  (are [xml path] (= (when xml (x/parse-str xml))
                     (cx/element-at-path parsed-sample-xml path))
       "<alpha>45</alpha>" [:inner :alpha]
       "<bravo>ovarb</bravo>" [:inner :bravo]
       nil [:foo]
       nil [:top :foo]
       nil [:foo :bar]))

(deftest content-at-path-test
  (are [expected path] (= expected (cx/content-at-path parsed-sample-xml path))
       ["45"] [:inner :alpha]
       ["ovarb"] [:inner :bravo]
       nil [:foo]))

(deftest string-at-path-test
  (are [expected path] (= expected (cx/string-at-path parsed-sample-xml path))
       "45" [:inner :alpha]
       "ovarb" [:inner :bravo]
       nil [:foo]
       nil [:inner :foo]))

(deftest long-at-path-test
  (is (= 45 (cx/long-at-path parsed-sample-xml [:inner :alpha])))
  (is (= nil (cx/long-at-path parsed-sample-xml [:inner :foo]))))

(deftest double-at-path-test
  (is (= 45.0 (cx/double-at-path parsed-sample-xml [:inner :alpha])))
  (is (= nil (cx/double-at-path parsed-sample-xml [:inner :foo]))))

(deftest bool-at-path-test
  (is (= true (cx/bool-at-path parsed-sample-xml [:inner :trueflag])))
  (is (= false (cx/bool-at-path parsed-sample-xml [:inner :falseflag])))
  (is (= nil (cx/bool-at-path parsed-sample-xml [:inner :foo]))))

(deftest datetime-at-path-test
  (is (= (t/date-time 1986 10 14 4 3 27 456) (cx/datetime-at-path parsed-sample-xml [:inner :datetime])))
  (is (= nil (cx/datetime-at-path parsed-sample-xml [:inner :foo]))))

(deftest attrs-at-path-test
  (is (= {:ia "1" :ib "foo"}
         (cx/attrs-at-path parsed-sample-xml
                           [:inner])))
  (is (= nil (cx/attrs-at-path parsed-sample-xml [:foo]))))

(def sample-multiple-elements-xml
  "<top>
    <inner ia=\"1\" ib=\"foo\">
      <alpha>45</alpha>
      <alpha>46</alpha>
      <bravo>ovarb</bravo>
      <bravo>ovary</bravo>
      <single>single_value</single>
      <datetime>1986-10-14T04:03:27.456Z</datetime>
      <datetime>1988-10-14T04:03:27.456Z</datetime>
      <single_datetime>1989-10-14T04:03:27.456Z</single_datetime>
    </inner>
  </top>")

(def parsed-sample-multiple-elements-xml
  (x/parse-str sample-multiple-elements-xml))

(deftest elements-at-path-test
  (are [xml path] (= (:content (x/parse-str xml))
                     (cx/elements-at-path parsed-sample-multiple-elements-xml path))
       "<a><alpha>45</alpha><alpha>46</alpha></a>" [:inner :alpha]
       "<a><bravo>ovarb</bravo><bravo>ovary</bravo></a>" [:inner :bravo]
       "<a><single>single_value</single></a>" [:inner :single]))

(deftest contents-at-path-test
  (are [expected path] (= expected (cx/contents-at-path parsed-sample-multiple-elements-xml path))
       [["45"] ["46"]] [:inner :alpha]
       [["ovarb"] ["ovary"]] [:inner :bravo]
       [["single_value"]] [:inner :single]
       [] [:foo]))

(deftest strings-at-path-test
  (are [expected path] (= expected (cx/strings-at-path parsed-sample-multiple-elements-xml path))
       ["45" "46"] [:inner :alpha]
       ["ovarb" "ovary"] [:inner :bravo]
       ["single_value"] [:inner :single]
       [] [:foo]
       [] [:inner :foo]))

(deftest datetimes-at-path-test
  (are [expected path] (= expected (cx/datetimes-at-path parsed-sample-multiple-elements-xml path))
       [(t/date-time 1986 10 14 4 3 27 456) (t/date-time 1988 10 14 4 3 27 456)] [:inner :datetime]
       [(t/date-time 1989 10 14 4 3 27 456)] [:inner :single_datetime]
       [] [:foo]
       [] [:inner :foo]))
