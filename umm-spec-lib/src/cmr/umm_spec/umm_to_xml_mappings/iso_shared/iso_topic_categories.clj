(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.iso-topic-categories
  "Functions for generating ISO-19115 and ISO-SMAP XML elements from UMM ISO topic categories."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories :as iso-topic-categories]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(defn generate-iso-topic-categories
  "Returns the content generator instructions for ISOTopicCategory"
  [c]
  (for [iso-topic-category (:ISOTopicCategories c)
        :let [xml-mapping (iso-topic-categories/umm->xml-iso-topic-category-map iso-topic-category)]
        :when xml-mapping]
    [:gmd:topicCategory
     [:gmd:MD_TopicCategoryCode xml-mapping]]))
