(ns cmr.search.test.results-handlers.metadata-results-handler
  (:require [clojure.test :refer :all]
            [clojure.data.xml :as x]
            [cmr.search.results-handlers.metadata-results-handler :as mrh]))

(deftest fix-parsed-xml-namespace-test
  (testing "DIF collection"
    (let [xml "<results><result><DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\"
              xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\"
              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
              xsi:schemaLocation=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/
              http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd\">
              <Entry_ID>geodata_1848</Entry_ID></DIF></result></results>"
              parsed (x/parse-str xml)]
      ;; Once no exception is thrown here, we can take out the workaround for fixing xml namespace
      (is (thrown-with-msg? javax.xml.stream.XMLStreamException
                            #"Prefix cannot be null"
                            (x/emit-str parsed)))
      (x/emit-str (mrh/fix-parsed-xml-namespace :dif :collection parsed))))

  (testing "ECHO10 collection"
    (let [xml "<results><result><Collection xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
              xsi:noNamespaceSchemaLocation=\"../schemas/Collection.xsd\">
              <ShortName>CZCSL3BCU</ShortName></Collection></result></results>"
              parsed (x/parse-str xml)]
      ;; Once no exception is thrown here, we can take out the workaround for fixing xml namespace
      (is (thrown-with-msg? javax.xml.stream.XMLStreamException
                            #"Prefix cannot be null"
                            (x/emit-str parsed)))
      (x/emit-str (mrh/fix-parsed-xml-namespace :echo10 :collection parsed))))

  (testing "ECHO10 granule"
    (let [xml "<results><result><Granule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
              xsi:noNamespaceSchemaLocation=\"../schemas/Collection.xsd\">
              <ShortName>CZCSL3BCU</ShortName></Granule></result></results>"
              parsed (x/parse-str xml)]
      ;; Once no exception is thrown here, we can take out the workaround for fixing xml namespace
      (is (thrown-with-msg? javax.xml.stream.XMLStreamException
                            #"Prefix cannot be null"
                            (x/emit-str parsed)))
      (x/emit-str (mrh/fix-parsed-xml-namespace :echo10 :granule parsed)))))

