(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute
  "Functions for parsing UMM additional attribute records out of ISO19115-2 XML documents."
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]))

