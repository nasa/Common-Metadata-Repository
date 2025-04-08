(ns cmr.transmit.config
  "Contains functions for retrieving application connection information from
  environment variables.

  The data which this namespace provides to CMR apps is used in all deployment
  environments for sharing configuration values that are transmitted over a
  private network, containing values that are specifically for service-to-service
  communications and not values to be accesed or evaluated over public networks.

  On occasion, application will set their publicly-facing configuration values
  with those that are defined here, but instances such as that are simply
  where there are values which are the same in both and not expected to change
  between public and private communications."
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.util :as util]
   [cmr.transmit.connection :as conn]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants for help in testing.

(def mock-echo-system-group-guid
  "The guid of the mock admin group."
  "mock-admin-group-guid")

(def mock-echo-system-token
  "A token for the mock system/admin user."
  "mock-echo-system-token")

(def local-system-test-user
  "A test user expected by ECHO in integration tests"
  "User101")

(def local-system-test-password
  "The test user's password"
  "Password101")

(def cmr-client-id
  "A client id to use as a header when communicating to other CMR services"
  "cmr-internal")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def token-header
  "authorization")

(def echo-token-header
  "echo-token")

(def revision-id-header
  "cmr-revision-id")

(def user-id-header
  "user-id")

(defmacro def-app-conn-config
  "Defines the following configuration entries for an application:
  * protocol
  * host
  * port
  * relative root URL"
  [app-name defaults]
  (let [protocol-config (symbol (str (name app-name) "-protocol"))
        host-config (symbol (str (name app-name) "-host"))
        port-config (symbol (str (name app-name) "-port"))
        relative-root-url-config (symbol (str (name app-name) "-relative-root-url"))]
    `(do
       (defconfig ~protocol-config
         ~(str "The protocol to use for connections to the " (name app-name) " application.")
         {:default ~(get defaults :protocol "http")})

       (defconfig ~host-config
         ~(str "The host name to use for connections to the " (name app-name) " application.")
         {:default ~(get defaults :host "localhost")})

       (defconfig ~port-config
         ~(str "The port number to use for connections to the " (name app-name) " application.")
         {:default ~(get defaults :port 3000) :type Long})

       (defconfig ~relative-root-url-config
         ~(str "Defines a root path that will appear on all requests sent to this application. For "
               "example if the relative-root-url is '/cmr-app' and the path for a URL is '/foo' then "
               "the full url would be http://host:port/cmr-app/foo. This should be set when this "
               "application is deployed in an environment where it is accessed through a VIP.")
         {:default ~(get defaults :relative-root-url "")}))))

(declare access-control bootstrap indexer ingest kms ordering metadata-db search
         urs virtual-product)
(def-app-conn-config access-control {:port 3011})
(def-app-conn-config bootstrap {:port 3006})
(def-app-conn-config indexer {:port 3004})
(def-app-conn-config ingest {:port 3002})
(def-app-conn-config kms {:port 2999, :relative-root-url "/kms"})
(def-app-conn-config ordering {:port 2999, :relative-root-url "/ordering/api"})
(def-app-conn-config metadata-db {:port 3001})
;; CMR open search is 3010
(def-app-conn-config search {:port 3003})
(def-app-conn-config urs {:port 3008})
(def-app-conn-config virtual-product {:port 3009})

(declare kms-scheme-override-json)
(defconfig kms-scheme-override-json
  "Allow for KMS schema urls to be overriden by setting this value to a JSON
   strings such as:
   {\"platforms\":\"static\",
    \"mime-type\":\"mimetype?format=csv&version=special\"}
    The reason this setting is needed is because CMR can only talk to the
    production KMS server. SIT, UAT, and Production may all need to be changed
    to KMS schema versions needed for these specific environments.
    Values can be set to either
    * the partual URL for KMS starting with the scheme name and parameters
    * fixed word 'static' which causes CMR to load an internal file
    Internal files are stored under indexer-app/resources/static_kms_keywords/
    "
  {:default ""})

(declare urs-username)
(defconfig urs-username
  "Defines the username that is sent from the CMR to URS to authenticate the CMR."
  {:default "mock-urs-username"})

(declare urs-password)
(defconfig urs-password
  "Defines the password that is sent from the CMR to URS to authenticate the CMR."
  {})

(declare local-edl-verification)
(defconfig local-edl-verification
  "Controls when cmr uses the EDL public key to locally verify JWT tokens."
  {:type Boolean
   :default true})

(declare edl-public-key)
(defconfig edl-public-key
  "Defines the EDL public key which is used to validate EDL JWT tokens locally.  Default is set to
   a locally generated EDL test jwk and is used in token unit tests"
  {:default "\n    {\n      \"kty\": \"RSA\",\n      \"n\": \"xSxiOkM8m8oCyWn-sNNZxBVTUcPAlhXRjKpLTYIM21epMC9rqEnrgL7iuntmp3UcffOIKtFHOtCG-jWUkyzxZHPPMo0kYZVHKRjGj-AVAy3FA-d2AtUc1dPlrQ0TpdDoTzew_6-48BcbdFEQI3161wcMoy40unYYYfzo3KuUeNcCY3cmHzSkYn4iQHaBy5zTAzKTIcYCTpaBGDk4_IyuysvaYmgwdeNO26hNV9dmPx_rWgYZYlashXZ_kRLirDaGpnJJHyPrYaEJpMIWuIfsh_UoMjsyuoQGe4XU6pG8uNnUd31mHa4VU78cghGZGrCz_YkPydfFlaX65LBp9aLdCyKkA66pDdnCkm8odVMgsH2x_kGM7sNlQ6ELTsT-dtJoiEDI_z3fSZehLw469QpTGQjfsfXUCYm8QrGckJF4bJc935TfGU86qr2Ik2YoipP_L4K_oqUf8i6bwO0iomo_C7Ukr4l-dh4D5O7szAb9Ga804OZusFk3JENlc1-RlB20S--dWrrO-v_L8WI2d72gizOKky0Xwzd8sseEqfMWdktyeKoaW0ANkBJHib4E0QxgedeTca0DH_o0ykMjOZLihOFtvDuCsbHG3fv41OQr4qRoX97QO2Hj1y3EBYtUEypan46g-fUyLCt-sYP66RkBYzCJkikCbzF_ECBDgX314_0\",\n      \"e\": \"AQAB\",\n      \"kid\": \"edljwtpubkey_development\"\n}"})

(defn mins->ms
  "Returns the number of minutes in milliseconds"
  [mins]
  (* mins 60000))

(declare http-socket-timeout)
(defconfig http-socket-timeout
  "The number of milliseconds before an HTTP request will timeout."
  ;; This is set to a value bigger than what appears to the VIP timeout. There's a problem with
  ;; EI-3988 where responses longer than 5 minutes never return. We want to cause those to fail.
  {:default (mins->ms 6)
   :type Long})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ECHO Rest

(declare echo-rest-protocol)
(defconfig echo-rest-protocol
  "The protocol to use when contructing ECHO Rest URLs."
  {:default "http"})

(declare echo-rest-host)
(defconfig echo-rest-host
  "The host name to use for connections to ECHO Rest."
  {:default "localhost"})

(declare echo-rest-port set-echo-rest-port!)
(defconfig echo-rest-port
  "The port to use for connections to ECHO Rest"
  {:default 3008 :type Long})

(declare mock-echo-port set-mock-echo-port!)
(defconfig mock-echo-port
  "The port to start mock echo and urs on"
  {:default 3008 :type Long})

(declare mock-echo-relative-root-url)
(defconfig mock-echo-relative-root-url
  "The relative root url for connections to Mock ECHO."
  {:default ""})

(declare echo-rest-context)
(defconfig echo-rest-context
  "The root context for connections to ECHO Rest."
  {:default ""})

(declare echo-http-socket-timeout)
(defconfig echo-http-socket-timeout
  "The number of milliseconds before an HTTP request to ECHO will timeout."
  {:default (mins->ms 60)
   :type Long})

(declare echo-system-token)
(defconfig echo-system-token
  "The ECHO system token to use for request to ECHO."
  {:default mock-echo-system-token})

(defn with-echo-system-token
  "Returns context map with ECHO system token assoc'ed under :token key."
  [context]
  (assoc context :token (echo-system-token)))

(declare echo-system-username)
(defconfig echo-system-username
  "The ECHO system token to use for request to ECHO."
  {:default "ECHO_SYS"})

(defn echo-system-token?
  "Returns true if the token passed in (or config map) is the echo system user's token or another
  token belonging to the ECHO system user"
  [token-or-context]
  (or (= (echo-system-token) token-or-context)
      (= (echo-system-token) (:token token-or-context))
      (= (echo-system-username) (util/lazy-get token-or-context :user-id))))

(declare administrators-group-name)
(defconfig administrators-group-name
  "The name of the Administrators group which the echo system user belongs to."
  {:default "Administrators"})

(declare administrators-group-legacy-guid)
(defconfig administrators-group-legacy-guid
  "The legacy guid of the administrators guid."
  {:default mock-echo-system-group-guid})

(def default-conn-info
  "The default values for connections."
  {:protocol "http"
   :context ""})

(declare metadata-db-protocol metadata-db-host metadata-db-port metadata-db-relative-root-url set-metadata-db-port!
         ingest-protocol ingest-host ingest-port ingest-relative-root-url
         access-control-protocol access-control-host access-control-port access-control-relative-root-url set-access-control-port!
         search-protocol search-host search-port search-relative-root-url
         indexer-protocol indexer-host indexer-port indexer-relative-root-url
         bootstrap-protocol bootstrap-host bootstrap-port bootstrap-relative-root-url
         virtual-product-protocol virtual-product-host virtual-product-port virtual-product-relative-root-url
         kms-protocol kms-host kms-port kms-relative-root-url
         ordering-protocol ordering-host ordering-port ordering-relative-root-url
         urs-protocol urs-host urs-port set-urs-port! urs-relative-root-url)

(defn app-conn-info
  "Returns the current application connection information as a map by application name"
  []
  {:metadata-db {:protocol (metadata-db-protocol)
                 :host (metadata-db-host)
                 :port (metadata-db-port)
                 :context (metadata-db-relative-root-url)}
   :ingest {:protocol (ingest-protocol)
            :host (ingest-host)
            :port (ingest-port)
            :context (ingest-relative-root-url)}
   :access-control {:protocol (access-control-protocol)
                    :host (access-control-host)
                    :port (access-control-port)
                    :context (access-control-relative-root-url)}
   :search {:protocol (search-protocol)
            :host (search-host)
            :port (search-port)
            :context (search-relative-root-url)}
   :indexer {:protocol (indexer-protocol)
             :host (indexer-host)
             :port (indexer-port)
             :context (indexer-relative-root-url)}
   :bootstrap {:protocol (bootstrap-protocol)
               :host (bootstrap-host)
               :port (bootstrap-port)
               :context (bootstrap-relative-root-url)}
   :virtual-product {:protocol (virtual-product-protocol)
                     :host (virtual-product-host)
                     :port (virtual-product-port)
                     :context (virtual-product-relative-root-url)}
   :echo-rest {:protocol (echo-rest-protocol)
               :host (echo-rest-host)
               :port (echo-rest-port)
               :context (echo-rest-context)}
   :kms {:protocol (kms-protocol)
         :host (kms-host)
         :port (kms-port)
         :context (kms-relative-root-url)}
   :ordering {:protocol (ordering-protocol)
              :host (ordering-host)
              :port (ordering-port)
              :context (ordering-relative-root-url)}
   :urs {:protocol (urs-protocol)
         :host (urs-host)
         :port (urs-port)
         :context (urs-relative-root-url)}})

(defn app-connection-system-key-name
  "The name of the app connection in the system"
  [app-name]
  (keyword (str (csk/->kebab-case-string app-name) "-connection")))

(defn context->app-connection
  "Retrieves the connection from the context for the given app."
  [context app-name]
  (or (get-in context [:system (app-connection-system-key-name app-name)])
      ;; The job scheduler uses a subset of the system context.
      (get context (app-connection-system-key-name app-name))))

(defn system-with-connections
  "Adds connection keys, with information like port numbers and URL to external resources, to the
   system map for the applications listed. They will be added in a way that can be retrieved with
   the context->app-connection function. All system.clj files will call this."
  [system app-names]
  (let [conn-info-map (app-conn-info)]
    (reduce (fn [sys app-name]
              (let [conn-info (merge default-conn-info (conn-info-map app-name))]
                (assoc sys
                       (app-connection-system-key-name app-name)
                       (conn/create-app-connection conn-info))))
            system
            app-names)))

(defn conn-params
  "Returns a map of connection params to merge in when making HTTP requests"
  [connection]
  {:connection-manager (conn/conn-mgr connection)
   :socket-timeout (http-socket-timeout)})

(defn format-public-root-url
  "Format the public root URL differently if a port is provided or not."
  [{:keys [protocol host port relative-root-url]
    :or {relative-root-url ""}}]
  (if port
    (format "%s://%s:%s%s/" protocol host port relative-root-url)
    (format "%s://%s%s/" protocol host relative-root-url)))

(defmulti application-public-root-url
  "Returns the public root url for an application given a context. Assumes
  public configuration is stored in a :public-conf key of the system."
  type)

(defmethod application-public-root-url clojure.lang.Keyword
  [app-key]
  (format-public-root-url (app-key (app-conn-info))))

(defmethod application-public-root-url :default
  [context]
  (format-public-root-url (get-in context [:system :public-conf])))

(declare status-app-url)
(defconfig status-app-url
  "The URL to be used for the status app in tophat"
  {:default "https://status.earthdata.nasa.gov/api/v1/notifications"
   :type String})
