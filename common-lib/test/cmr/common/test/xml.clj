(ns cmr.common.test.xml
  (:require
   [clj-time.core :as t]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.test :refer [are deftest is]]
   [cmr.common.xml :as cx]))

(deftest remove-xml-processing-instructions-test
  ;; expected contains some things that should be left alone
  (let [expected "<foo><bar>&lt;!DOCTYPE ?xml?&gt;</bar></foo>"
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
  <trueflagOne>1</trueflagOne>
  <falseflag>false</falseflag>
  <falseflagZero>0</falseflagZero>
  <datetime>1986-10-14T04:03:27.456Z</datetime>
  </inner>
  </top>")

(def parsed-sample-xml
  (xml/parse-str sample-xml))

(deftest element-at-path-test
  (are [xml path] (= (when xml (xml/parse-str xml))
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
  (is (identical? true (cx/bool-at-path parsed-sample-xml [:inner :trueflag])))
  (is (identical? true (cx/bool-at-path parsed-sample-xml [:inner :trueflagOne])))
  (is (identical? false (cx/bool-at-path parsed-sample-xml [:inner :falseflag])))
  (is (identical? false (cx/bool-at-path parsed-sample-xml [:inner :falseflagZero])))
  (is (nil? (cx/bool-at-path parsed-sample-xml [:inner :foo]))))

(deftest datetime-at-path-test
  (is (= (t/date-time 1986 10 14 4 3 27 456)
         (cx/datetime-at-path parsed-sample-xml [:inner :datetime])))
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
  (xml/parse-str sample-multiple-elements-xml))

(deftest elements-at-path-test
  (are [xml path] (= (:content (xml/parse-str xml))
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

(def iso-xml
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <gmi:MI_Metadata xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
  xmlns:gco=\"http://www.isotc211.org/2005/gco\"
  xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
  xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
  xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
  xmlns:gss=\"http://www.isotc211.org/2005/gss\"
  xmlns:gts=\"http://www.isotc211.org/2005/gts\"
  xmlns:srv=\"http://www.isotc211.org/2005/srv\"
  xmlns:gml=\"http://www.opengis.net/gml/3.2\"
  xmlns:xlink=\"http://www.w3.org/1999/xlink\"
  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
  xmlns:swe=\"http://schemas.opengis.net/sweCommon/2.0/\"
  xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><!--Other Properties, all:0, coi:0,ii:0,si:0,"
       "pli:0,pri:0,qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->
  <gmd:fileIdentifier>
  <gco:CharacterString>gov.nasa.echo:DatasetID</gco:CharacterString>
  </gmd:fileIdentifier>
  </gmi:MI_Metadata>"))

(def pretty-printed-iso-xml
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gmi:MI_Metadata\n"
       "    xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"\n"
       "    xmlns:gco=\"http://www.isotc211.org/2005/gco\"\n"
       "    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"\n"
       "    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"\n"
       "    xmlns:gml=\"http://www.opengis.net/gml/3.2\"\n"
       "    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"\n"
       "    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"\n"
       "    xmlns:gss=\"http://www.isotc211.org/2005/gss\"\n"
       "    xmlns:gts=\"http://www.isotc211.org/2005/gts\"\n"
       "    xmlns:srv=\"http://www.isotc211.org/2005/srv\"\n"
       "    xmlns:swe=\"http://schemas.opengis.net/sweCommon/2.0/\"\n"
       "    xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n"
       "    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"
       "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
       "    <!--Other Properties, all:0, coi:0,ii:0,si:0,pli:0,pri:0,"
       "qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->\n"
       "    <gmd:fileIdentifier>\n"
       "        <gco:CharacterString>gov.nasa.echo:DatasetID</gco:CharacterString>\n"
       "    </gmd:fileIdentifier>\n</gmi:MI_Metadata>\n"))

(deftest pretty-print-xml-test
  (is (= pretty-printed-iso-xml (cx/pretty-print-xml iso-xml))))

(def valid-xml
  "<?xml version= \"1.0\" ?><Collection><ShortName>test</ShortName><VersionId>22</VersionId></Collection>")

(def too-much-xml
  "<?xml version= \"1.0\" ?><Collection><ShortName>test</ShortName><VersionId>22</VersionId><NewField>test</NewField></Collection>")

(def bad-xml
  "<?xml version= \"1.0\" ?><Collection><ShortName>test</ShortName><VersionId>22</VersionId><NewField>test</Collection>")

(def xxe-xml
  "<?xml version= \"1.0\" ?><!DOCTYPE Collection [<!ENTITY xxe SYSTEM \"file:///etc/passwd\" >] ><Collection><ShortName>test</ShortName><VersionId>&xxe;1</VersionId></Collection>")

(def test-schema
  "<?xml version=\"1.0\" encoding=\"us-ascii\"?>
  <xs:schema elementFormDefault=\"qualified\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">
    <xs:element name=\"Collection\" type=\"Collection\" />
    <!-- #mark Collection Type -->
    <xs:complexType name=\"Collection\">
      <xs:sequence>
        <xs:element name=\"ShortName\" type=\"CollectionShortName\"></xs:element>
        <xs:element name=\"VersionId\">
          <xs:annotation>
            <xs:documentation>The version of an input collection and/or a dependent collection that is somehow associated with this collection.</xs:documentation>
          </xs:annotation>
          <xs:simpleType>
            <xs:restriction base=\"xs:string\">
              <xs:maxLength value=\"80\" />
              <xs:minLength value=\"1\"></xs:minLength>
            </xs:restriction>
          </xs:simpleType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
    <xs:simpleType name=\"CollectionShortName\">
      <xs:restriction base=\"xs:string\">
        <xs:maxLength value=\"85\" />
        <xs:minLength value=\"1\"></xs:minLength>
      </xs:restriction>
    </xs:simpleType>
  </xs:schema>")

(deftest validating-xml-test
  (let [schema-url test-schema]
    (is (nil? (cx/validate-xml schema-url valid-xml)))
    (is (string/includes? (first (cx/validate-xml schema-url too-much-xml)) "Invalid content was found starting with element 'NewField'."))
    (is (string/includes? (first (cx/validate-xml schema-url bad-xml)) "must be terminated by the matching end-tag"))
    ;; The DocType parsing has been disabled and no errors should be 
    ;; given, but warning log message is produced.
    (is (nil? (first (cx/validate-xml schema-url xxe-xml))))))
