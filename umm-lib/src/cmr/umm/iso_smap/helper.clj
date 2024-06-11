(ns cmr.umm.iso-smap.helper
  "Contains functions used by SMAP ISO collection and granule generation"
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [clj-time.format :as f]))

(defn xml-elem-with-path-value
  "Returns the identification element with the given path and value"
  [id-elems path value]
  (first (filter #(= value (cx/string-at-path % path)) id-elems)))

(defn xml-elem-with-title-tag
  "Returns the identification element with the given tag"
  [id-elems tag]
  (xml-elem-with-path-value id-elems [:citation :CI_Citation :title :CharacterString] tag))

(def iso-header-attributes
  "The set of attributes that go on the iso smap root element"
  {:xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"})

(defn scope-code-element
  [code-value]
  (xml/element
    :gmd:MD_ScopeCode
    {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
     :codeListValue code-value} code-value))

(def iso-charset-element
  "Defines the iso-charset-element"
  (let [iso-code-list-attributes
        {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode"
         :codeListValue "utf8"}]
    (xml/element :gmd:characterSet {}
               (xml/element :gmd:MD_CharacterSetCode iso-code-list-attributes "utf8"))))

(defn iso-hierarchy-level-element
  "Defines the iso-hierarchy-level-element"
  [code-value]
  (xml/element :gmd:hierarchyLevel {} (scope-code-element code-value)))

(defn iso-string-element
  "Returns the iso element with gco:CharacterString that holds the given string value"
  [key value]
  (xml/element key {}
             (xml/element :gco:CharacterString {} value)))

(defn- iso-date-type-element
  "Returns the iso date type element for the given type"
  [type]
  (xml/element
    :gmd:dateType {}
    (xml/element
      :gmd:CI_DateTypeCode
      {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
       :codeListValue type} type)))

(defn iso-date-element
  "Returns the iso date element based on the given type and date"
  ([type date]
   (iso-date-element type date false))
  ([type date date-only?]
   (xml/element :gmd:date {}
              (xml/element :gmd:CI_Date {}
                         (xml/element :gmd:date {}
                                    (if date-only?
                                      (xml/element :gco:Date {} (f/unparse (f/formatters :date) date))
                                      (xml/element :gco:DateTime {} (str date))))
                         (iso-date-type-element type)))))

(defn- generate-identifer-element
  "Returns the smap iso identifier element for the given key and value"
  [key value]
  (xml/element
    :gmd:identifier {}
    (xml/element
      :gmd:MD_Identifier {}
      (iso-string-element :gmd:code value)
      (iso-string-element :gmd:codeSpace "smap.jpl.nasa.gov")
      (iso-string-element :gmd:description key))))

(defn generate-short-name-element
  "Returns the smap iso short name element"
  [short-name]
  (generate-identifer-element "The ECS Short Name" short-name))

(defn generate-version-id-element
  "Returns the smap iso version id element"
  [version-id]
  (generate-identifer-element "The ECS Version ID" version-id))

(defn generate-citation-element
  "Returns the citation element with the given title and datetime"
  [title type date]
  (xml/element :gmd:citation {}
             (xml/element :gmd:CI_Citation {}
                        (iso-string-element :gmd:title title)
                        (iso-date-element type date))))

(defn generate-dataset-id-element
  "Returns the smap iso dataset id element"
  [dataset-id update-time]
  (xml/element
    :gmd:identificationInfo {}
    (xml/element
      :gmd:MD_DataIdentification {}
      (generate-citation-element "DataSetId" "revision" update-time)
      (iso-string-element :gmd:abstract "DataSetId")
      (xml/element :gmd:aggregationInfo {}
                 (xml/element :gmd:MD_AggregateInformation {}
                            (xml/element :gmd:aggregateDataSetIdentifier {}
                                       (xml/element :gmd:MD_Identifier {}
                                                  (iso-string-element :gmd:code dataset-id)))
                            (xml/element :gmd:associationType {})))
      (iso-string-element :gmd:language "eng"))))

(defn generate-datetime-element
  "Returns the smap iso update-time/insert-time element"
  [title date-type datetime]
  (xml/element
    :gmd:identificationInfo {}
    (xml/element
      :gmd:MD_DataIdentification {}
      (generate-citation-element title date-type datetime)
      (iso-string-element :gmd:abstract title)
      (iso-string-element :gmd:purpose title)
      (iso-string-element :gmd:language "eng"))))

(defn role-code-attributes
  "Returns the role code attributes for the given role type"
  [role]
  {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_RoleCode"
   :codeListValue role})
