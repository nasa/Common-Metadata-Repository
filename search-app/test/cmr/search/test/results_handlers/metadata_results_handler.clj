(ns cmr.search.test.results-handlers.metadata-results-handler
  (:require [clojure.test :refer :all]
            [clojure.data.xml :as x]
            [cmr.search.results-handlers.metadata-results-handler :as mrh]))

(def dif-xml "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\"
             xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\"
             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
             xsi:schemaLocation=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/
             http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd\">
             <Entry_ID>geodata_1848</Entry_ID></DIF>")

(def echo10-coll-xml "<Collection xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                     xsi:noNamespaceSchemaLocation=\"../schemas/Collection.xsd\">
                     <ShortName>CZCSL3BCU</ShortName></Collection>")

(def echo10-gran-xml "<Granule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                     xsi:noNamespaceSchemaLocation=\"../schemas/Collection.xsd\">
                     <ShortName>CZCSL3BCU</ShortName></Granule>")

(defn- search-result-xml
  "Returns the given xml wrapped in search result format"
  [xml]
  (str "<results><result>" xml "</result></results>"))

(deftest fix-parsed-xml-namespace-test
  (testing "DIF collection"
    (are [xml result-format concept-path]
         (let [parsed (x/parse-str xml)]
           ;; Once no exception is thrown here, we can take out the workaround for fixing xml namespace
           (is (thrown-with-msg? javax.xml.stream.XMLStreamException
                                 #"Prefix cannot be null"
                                 (x/emit-str parsed)))
           (x/emit-str (mrh/fix-parsed-xml-namespace parsed result-format concept-path)))

         dif-xml :dif []
         (search-result-xml dif-xml) :dif [:result :DIF]
         echo10-coll-xml :echo10 []
         (search-result-xml echo10-coll-xml) :echo10 [:result :Collection]
         echo10-gran-xml :echo10 []
         (search-result-xml echo10-gran-xml) :echo10 [:result :Granule])))
