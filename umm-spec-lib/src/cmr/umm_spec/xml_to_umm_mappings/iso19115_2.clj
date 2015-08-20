(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require clojure.string
            [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

;;; Path Utils

;; ISO formats tend to have *very* long xpaths with many redundant elements.

(def md-data-id-root-path
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification")

(defn- make-data-id-xpath
  "Returns xpath from MD_DatatIdentification element."
  [& parts]
  (clojure.string/join "/" (cons md-data-id-root-path parts)))

(defn- make-extent-xpath
  [& parts]
  (apply make-data-id-xpath "gmd:extent/gmd:EX_Extent" parts))

(defn- make-temporal-xpath
  [& parts]
  (apply make-extent-xpath "gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent" parts))

(def citation-base-xpath
  (make-data-id-xpath "gmd:citation/gmd:CI_Citation"))

(def entry-id-xpath
  (xpath (str citation-base-xpath "/gmd:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")))

(def entry-title-xpath
  (xpath (str citation-base-xpath "/gmd:title/gco:CharacterString")))

;;; Mapping

(def iso19115-2-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object {:EntryId entry-id-xpath
             :EntryTitle entry-title-xpath
             :Abstract (xpath (make-data-id-xpath "gmd:abstract/gco:CharacterString"))
             :Purpose (xpath (make-data-id-xpath "/gmd:purpose/gco:CharacterString"))
             :DataLanguage (xpath (make-data-id-xpath "gmd:language/gco:CharacterString"))
             :TemporalExtents (for-each (make-temporal-xpath)
                               (object {:RangeDateTimes (for-each "gml:TimePeriod"
                                                         (object {:BeginningDateTime (xpath "gml:beginPosition")
                                                                  :EndingDateTime    (xpath "gml:endPosition")}))
                                        :SingleDateTimes (select "gml:TimeInstant/gml:timePosition")}))
             })))
