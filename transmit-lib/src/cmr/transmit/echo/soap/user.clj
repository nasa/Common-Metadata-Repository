(ns cmr.transmit.echo.soap.user
  "Helper to perform User tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.string :as str]))

(def user-keys
  "Keys within a user map."
  [:password :guid :user-domain :user-region :primary-study-area :user-type :username :title
   :first-name :middle-initial :last-name :param-map :email :opt-in :organization-name :addresses
   :phones :roles :creation-date])

(def minimal-user
  "A minimally value user map"
  {:user-domain "GOVERNMENT"
   :primary-study-area  "AIR_SEA_INTERACTION" :user-type  "PRODUCTION_USER"
   :first-name  "Admin" :last-name  "User" :email  "admin@example.com"
   :user-region "USA" :organization-name "ECHO"
   :opt-in "false" :addresses [["ns3:Country" "USA"]]})

(defn create-user
  "Perform a CreateUser request against the SOAP API.  Takes a map containing request parameters:
    [:token :password :guid :user-domain :user-region :primary-study-area :user-type :username :title
     :first-name :middle-initial :last-name :param-map :email :opt-in :organization-name :addresses
     :phones :roles :creation-date]"
  [param-map]
  (let [{:keys [token password guid user-domain user-region primary-study-area user-type username title
                first-name middle-initial last-name param-map email opt-in organization-name addresses
                phones roles creation-date]} param-map
        body ["ns2:CreateUser"
                soap/soap-ns-map
                ["ns2:token" token]
                ["ns2:password" password]
                ["ns2:newUser"
                  ;; NOTE the when forms arent really necessary as empty elements will be ommitted when we convert
                  ;; to XML anyway, but this makes it easier to see which elements are required and which arent.
                  ;;  Ideally we will implement a better approach.
                  ["ns3:UserDomain" (or user-domain "OTHER")]
                  ["ns3:UserRegion" (or user-region "USA")]
                  (when primary-study-area ["ns3:PrimaryStudyArea" primary-study-area])
                  (when user-type ["ns3:UserType" user-type])
                  ["ns3:Username" username]
                  (when title ["ns3:Title" title])
                  ["ns3:FirstName" first-name]
                  (when middle-initial ["ns3:MiddleInitial" middle-initial])
                  ["ns3:LastName" last-name]
                  ["ns3:Email" email]
                  ["ns3:OptIn" (or opt-in "false")]
                  (when organization-name ["ns3:OrganizationName" organization-name])
                  ;; For now, addresses, phones, and roles need to be passed in already in hiccup format
                  ["ns3:Addresses" (soap/item-list (or addresses [["ns3:Country" "USA"]]))]
                  (when phones ["ns3:Phones" (soap/item-list phones)])
                  (when roles ["ns3:Roles" (soap/item-list roles)])
                  (when creation-date ["ns3:CreationDate" creation-date])]]]
      (-> (soap/post-soap :user body)
          (soap/extract-string :create-user))))

(defn get-user-by-user-name-case-sensitive
  "Perform a GetUserByUserName request against the SOAP API.  Takes a map containing request parameters:
    [:token :user-name]
    Note that for now this returns a flat map, and does not contain nested elements like addresses."
  [token user-name]
  (let [body ["ns2:GetUserByUserName"
              soap/soap-ns-map
              ["ns2:token" token]
              ["ns2:userName" user-name]]]
    (-> (soap/post-soap :user body)
        (soap/extract-item-map :get-user-by-user-name user-keys))))

(defn get-user-by-user-name
  "Perform a GetUserByUserName request against the SOAP API.  Takes a map containing request parameters:
    [:token :user-name]
    Note that for now this returns a flat map, and does not contain nested elements like addresses."
  [param-map]
  (let [{:keys [token user-name]} param-map]
    (or (get-user-by-user-name-case-sensitive token (str/lower-case user-name))
        (get-user-by-user-name-case-sensitive token user-name))))

(defn get-current-user
  "Perform a GetCurrentUser request against the SOAP API using the provided token."
  [token]
  (let [body ["ns2:GetCurrentUser"
                soap/soap-ns-map
                ["ns2:token" token]]]
      (-> (soap/post-soap :user body)
          (soap/extract-item-map :get-current-user user-keys))))
