(ns cmr.umm.xml-schema-validator
  "Provides functions for validating XML against a schema."
  (:require [clojure.java.io :as io])
  (:import javax.xml.validation.SchemaFactory
           javax.xml.XMLConstants
           javax.xml.transform.stream.StreamSource
           org.xml.sax.ext.DefaultHandler2
           java.io.StringReader))

(defn- sax-parse-exception->str
  "Converts a SaxParseException to a String"
  [e]
  (format "Line %d - %s" (.getLineNumber e) (.getMessage e)))

(defn- create-error-handler
  "Creates an instance of DefaultHandler2 that will save all schema errors in the errors-atom."
  [errors-atom]
  (proxy [DefaultHandler2] []
    (error
      [e]
      (swap! errors-atom conj (sax-parse-exception->str e)))
    (fatalError
      [e]
      (swap! errors-atom conj (sax-parse-exception->str e)))))

(defn validate-xml
  "Validates the XML against the schema in the given resource. schema-resource should be a classpath
  resource as returned by clojure.java.io/resource.
  Returns a list of errors in the XML schema."
  [schema-resource xml]
  (let [factory (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
        schema (.newSchema factory schema-resource)
        validator (.newValidator schema)
        errors-atom (atom [])]
    (.setErrorHandler validator (create-error-handler errors-atom))
    (.validate validator (StreamSource. (StringReader. xml)))
    @errors-atom))


(comment
  (let [xml "<Collection>
            <ShortName>MINIMAL</ShortName>
            <VersionId>1</VersionId>
            <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
            <LastUpdate>1dd999-12-31T19:00:00-05:00</LastUpdate>
            <LongName>A minimal valid collection</LongName>
            <DataSetId>A minimal valid collection V 1</DataSetId>
            <Description>A minimal valid collection</Description>
            <Orderable>true</Orderable>
            <Visible>true</Visible>
            </Collection>"
            schema (io/resource "schema/echo10/Collection.xsd")]
    (validate-xml schema xml))

  (let [g1-xml "<Granule>
               <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
               <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
               <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
               <Collection>
               <DataSetId>AQUARIUS_L1A_SSS:1</DataSetId>
               </Collection>
               <RestrictionFlag>0.0</RestrictionFlag>
               <Orderable>false</Orderable>
               </Granule>"
               schema (io/resource "schema/echo10/Granule.xsd")]
    (validate-xml schema g1-xml))

  (let [g3-xml "<Granule>
               <GranuleUR>GranuleUR100</GranuleUR>
               <InsertTime>2010-01-05T05:30:30.550-05:00</InsertTime>
               <LastUpdate>2010-01-05T05:30:30.550-05:00</LastUpdate>
               <Collection>
               <ShortName>TESTCOLL-100</ShortName>
               <VersionId>1.0</VersionId>
               </Collection>
               <RestrictionFlag>0.0</RestrictionFlag>
               <Orderable>true</Orderable>
               </Granule>"
               xml-struct (clojure.data.xml/parse-str g3-xml)
               granule-content (cmr.common.xml/content-at-path xml-struct [:Granule])
               coll-elem-content (cmr.common.xml/content-at-path granule-content [:Collection])]
    (cmr.common.xml/string-at-path granule-content [:Collection :ShortName]))
  )