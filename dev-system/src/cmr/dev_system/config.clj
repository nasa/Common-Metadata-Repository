(ns cmr.dev-system.config
  (:require
   [cmr.common.config :as config :refer [defconfig]]))

(defn- base-parse-dev-system-component-type
  "Parse the component type and validate it against the given set."
  [value valid-types-set]
  (when-not (valid-types-set value)
    (throw (Exception. (str "Unexpected component type value:" value))))
  (keyword value))

(defn parse-dev-system-component-type
  "Parse the component type and validate it is either in-memory or external."
  [value]
  (base-parse-dev-system-component-type value #{"in-memory" "external"}))

(defn parse-dev-system-queue-type
  "Parse the component type and validate it one of the valid queue types."
  [value]
  (base-parse-dev-system-component-type value #{"in-memory" "aws" "external"}))

(defconfig external-echo-port
  "Specifies the port on which to connect for external echo."
  {:default 10000
   :type Long})

(defconfig embedded-kibana-port
  "Specifies port to run an embedded kibana on."
  {:default 5601
   :type Long})

(defconfig use-web-compression?
  "Indicates whether the servers will use gzip compression. Disable this to
  make tcpmon usable"
  {:default true
   :type Boolean})

(defconfig use-access-log
  "Indicates whether the servers will use the access log."
 {:default false
  :type Boolean})

(defconfig dev-system-echo-type
  "Specifies whether dev system should run an in-memory mock ECHO or use an
  external ECHO."
  {:default :in-memory
   :parser parse-dev-system-component-type})

(defconfig dev-system-db-type
  "Specifies whether dev system should run an in-memory database or use an
  external database."
  {:default :in-memory
   :parser parse-dev-system-component-type})

(defconfig dev-system-queue-type
  "Specifies whether dev system should skip the use of a message queue or
  use a Rabbit MQ or
  AWS SNS/SQS message queue"
  {:default :in-memory
   :parser parse-dev-system-queue-type})

(defconfig dev-system-elastic-type
  "Specifies whether dev system should run an in-memory elasticsearch or
  use an external instance."
  {:default :in-memory
   :parser parse-dev-system-component-type})

(defconfig dev-system-redis-type
  "Specifies whether dev system should run an in-memory redis or
  use an external instance."
  {:default :in-memory
   :parser parse-dev-system-component-type})
