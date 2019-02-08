(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.collection-citation
  "Functions for parsing UMM collection citation records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(def individual-name-xpath
  "gmd:individualName/gco:CharacterString")

(def organisation-name-xpath
  "gmd:organisationName/gco:CharacterString")

(def position-name-xpath
  "gmd:positionName/gco:CharacterString")

(def creator-editor-xpath
  (str "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty"
       "[gmd:role/gmd:CI_RoleCode/@codeListValue='author']"))

(def publisher-release-place-xpath
  (str "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty"
       "[gmd:role/gmd:CI_RoleCode/@codeListValue='publisher']"))

(def online-resource-xpath
  (str "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty"
       "[gmd:role/gmd:CI_RoleCode/@codeListValue='resourceProvider']"))

(def title-xpath
  "gmd:title/gco:CharacterString")

(def series-name-xpath
  "gmd:series/gmd:CI_Series/gmd:name/gco:CharacterString")

(def release-date-xpath
  "gmd:editionDate/gco:DateTime")

(def delivery-point-xpath
  "gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:deliveryPoint/gco:CharacterString")

(def city-xpath
  "gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:city/gco:CharacterString")

(def state-xpath
  "gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:administrativeArea/gco:CharacterString")

(def zip-xpath
  "gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:postalCode/gco:CharacterString")

(def country-xpath
  "gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:country/gco:CharacterString")

(def email-xpath
  "gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:electronicMailAddress/gco:CharacterString")

(def version-xpath
  "gmd:edition/gco:CharacterString")

(def issue-identification-xpath
  "gmd:series/gmd:CI_Series/gmd:issueIdentification/gco:CharacterString")

(def data-presentation-form-xpath
  "gmd:presentationForm/gmd:CI_PresentationFormCode/@codeListValue")

(def other-citation-details-xpath
  "gmd:otherCitationDetails/gco:CharacterString")

(def linkage-xpath
  (str "gmd:contactInfo/gmd:CI_Contact"
       "/gmd:onlineResource/gmd:CI_OnlineResource/gmd:linkage/gmd:URL"))

(def protocal-xpath
  (str "gmd:contactInfo/gmd:CI_Contact"
       "/gmd:onlineResource/gmd:CI_OnlineResource/gmd:protocol/gco:CharacterString"))

(def application-profile-xpath
  (str "gmd:contactInfo/gmd:CI_Contact"
       "/gmd:onlineResource/gmd:CI_OnlineResource/gmd:applicationProfile/gco:CharacterString"))

(def name-xpath
  (str "gmd:contactInfo/gmd:CI_Contact"
       "/gmd:onlineResource/gmd:CI_OnlineResource/gmd:name/gco:CharacterString"))

(def description-xpath
  (str "gmd:contactInfo/gmd:CI_Contact"
       "/gmd:onlineResource/gmd:CI_OnlineResource/gmd:description/gco:CharacterString"))

(def function-xpath
  (str "gmd:contactInfo/gmd:CI_Contact"
       "/gmd:onlineResource/gmd:CI_OnlineResource/gmd:function/gmd:CI_OnLineFunctionCode/@codeListValue"))

(defn- get-creator-editor
  "Get creator, editor from the creator-editor-parties.
   They share the same structure except that editor contains additional position-name."
  [creator-editor-parties]
  (into (sorted-map)
    (for [creator-editor-party creator-editor-parties
          :let [idname (value-of creator-editor-party individual-name-xpath)
                osname (value-of creator-editor-party organisation-name-xpath)
                osname (when osname
                         (str " " osname))
                psname (value-of creator-editor-party position-name-xpath)
                idosname (str idname osname)]]
      (if (= psname "editor")
        {:editor (when idosname (str/trim idosname))}
        {:creator (when idosname (str/trim idosname))}))))

(defn- get-publisher-release-place
  "Get publisher, release place from the pub-rel-pl-parties.
   They share the same structure except that release place contains additional position-name."
  [pub-rel-pl-parties]
  (into (sorted-map)
    (for [pub-rel-pl-party pub-rel-pl-parties
          :let [idname (value-of pub-rel-pl-party individual-name-xpath)
                osname (value-of pub-rel-pl-party organisation-name-xpath)
                osname (when osname
                         (str " " osname))
                psname (value-of pub-rel-pl-party position-name-xpath)
                idosname (str idname osname)
                del-pt (value-of pub-rel-pl-party delivery-point-xpath)
                city (value-of pub-rel-pl-party city-xpath)
                city (when city
                       (str " " city))
                state (value-of pub-rel-pl-party state-xpath)
                state (when state
                        (str " " state))
                zip (value-of pub-rel-pl-party zip-xpath)
                zip (when zip
                      (str " " zip))
                country (value-of pub-rel-pl-party country-xpath)
                country (when country
                          (str " " country))
                email (value-of pub-rel-pl-party email-xpath)
                email (when email
                        (str " " email))
                release-place (str del-pt city state zip country email)]]
      (if (= psname "release place")
        {:release-place (when release-place (str/trim release-place))}
        {:publisher (when idosname (str/trim idosname))}))))

(defn- get-online-resources
  "Get the online resources."
  [online-resource-parties]
  (for [online-resource-party online-resource-parties
        :let [linkage (value-of online-resource-party linkage-xpath)
              protocol (value-of online-resource-party protocal-xpath)
              app-profile (value-of online-resource-party application-profile-xpath)
              name (value-of online-resource-party name-xpath)
              description (value-of online-resource-party description-xpath)
              function (value-of online-resource-party function-xpath)]]
    (util/remove-nil-keys
      (when linkage
        {:Linkage linkage
         :Protocol protocol
         :ApplicationProfile app-profile
         :Name name
         :Description description
         :Function function}))))

(defn parse-collection-citation
  "Parse the Collection Citation from XML resource citation"
  [doc collection-citation-xpath sanitize?]
  (let [resource-citations (seq (select doc collection-citation-xpath))]
    (for [resource-citation resource-citations
          :let [release-date (date/sanitize-and-parse-date
                               (value-of resource-citation release-date-xpath) sanitize?)
                creator-editor-parties (seq (select resource-citation creator-editor-xpath))
                pub-rel-pl-parties (seq (select resource-citation publisher-release-place-xpath))
                online-resource-parties (seq (select resource-citation online-resource-xpath))
                creator (:creator (get-creator-editor creator-editor-parties))
                editor (:editor (get-creator-editor creator-editor-parties))
                publisher (:publisher (get-publisher-release-place pub-rel-pl-parties))
                release-place (:release-place (get-publisher-release-place pub-rel-pl-parties))
                online-resources (remove #(nil? (seq %))(get-online-resources online-resource-parties))]]
      (util/remove-nil-keys
        {:Creator creator
         :Editor editor
         :Title  (value-of resource-citation title-xpath)
         :SeriesName (value-of resource-citation series-name-xpath)
         :ReleaseDate (if sanitize?
                        (when (date/valid-date? release-date)
                          release-date)
                        release-date)
         :ReleasePlace release-place
         :Publisher publisher
         :Version (value-of resource-citation version-xpath)
         :IssueIdentification (value-of resource-citation issue-identification-xpath)
         :DataPresentationForm (value-of resource-citation data-presentation-form-xpath)
         :OtherCitationDetails (value-of resource-citation other-citation-details-xpath)
         :OnlineResource (first online-resources)}))))
