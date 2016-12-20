(ns cmr.indexer.data.concepts.collection.opendata
  "Contains functions to convert collection into opendata related elasticsearch documents")

(defn- email-contact?
  "Return true if the given person has an email as contact info."
  [person]
  (some #(= "Email" (:Type %))
        (get-in person [:ContactInformation :ContactMechanisms])))

(defn- data-center-contacts
  "Returns the data center contacts with ContactInformation added if it doesn't have contact info"
  [data-center]
  (let [contacts (concat (:ContactPersons data-center) (:ContactGroups data-center))]
    (map (fn [contact]
           (if (:ContactInformation contact)
             contact
             (assoc contact :ContactInformation (:ContactInformation data-center))))
         contacts)))

(defn get-contact-personnel
  "Return a collection of personnel names and contact mechanisms out of:
  ContactPersons, ContactGroups, and DataCenters"
  [collection]
  (let [{:keys [ContactPersons ContactGroups DataCenters]} collection
        contacts (concat ContactPersons ContactGroups (mapcat data-center-contacts DataCenters))]
   (filter #(not= (:FirstName %) nil) contacts)))



(defn opendata-email-contact
  "Returns the opendata email contact info for the given collection, it is just the first email
  contact info found in the ContactPersons, ContactGroups or DataCenters."
  [collection]
  (let [{:keys [ContactPersons ContactGroups DataCenters]} collection
        contacts (concat ContactPersons ContactGroups (mapcat data-center-contacts DataCenters))
        email-contact (some #(when (email-contact? %) %) contacts)]
    (when email-contact
      (let [email (some #(when (= "Email" (:Type %)) (:Value %))
                        (get-in email-contact [:ContactInformation :ContactMechanisms]))
            email-contacts (when email [{:type :email :value email}])]
        {:first-name (:FirstName email-contact)
         :middle-name (:MiddleName email-contact)
         :last-name (:LastName email-contact)
         :roles (:Roles email-contact)
         :contacts email-contacts}))))

(defn related-url->opendata-related-url
  "Returns the opendata related url for the given collection related url"
  [related-url]
  (let [{:keys [Title Description Relation URLs MimeType FileSize]} related-url
        {:keys [Size Unit]} FileSize
        size (when (or Size Unit) (str Size Unit))]
    ;; See CMR-3446. The current UMM JSON RelatedUrlType is flawed in that there can be multiple
    ;; URLs, but only a single Title, MimeType and FileSize. This model doesn't make sense.
    ;; Talked to Erich and he said that we are going to change the model.
    ;; So for now, we make the assumption that there is only one URL in each RelatedUrlType.
    {:type (first Relation)
     :sub-type (second Relation)
     :url (first URLs)
     :description Description
     :mime-type MimeType
     :title Title
     :size size}))
