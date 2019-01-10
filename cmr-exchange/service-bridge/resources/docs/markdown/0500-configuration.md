# Configuration


**Contents**

* Modes of Configuration
* Order of Precedence
* Data Structure


## Modes of Configuration

cmr-service-bridge is configured in several ways:

* **Configuration file** (see `resources/config/cmr-opendap/config.edn`) - this is
  generally for providing more-or-less static defaults.
* **Java system properties** - provided either with the `-Dsome.prop.name=value` as
  a command line option, or in the `project.clj` file with
  `:jvm-opts=["-Dcmr.some.prop.name=value"]`. This is the recommended way to set
  values for local development environments. Note that only property names
  starting with "cmr." will be recognized. Also: configuration will be nested
  under keys created from the property name, so the above example would be
  available as `{:cmr {:some {:prop {:name "value"}}}}`. Values that can be
  parsed as integers are converted to integers in the resulting configuration
  data structure.
* **Environment variables** - this is the recommended way to override
  configuration values in different deployment environments. Environment
  variables must be prefixed with `CMR_`, `HTTPD_`, or `LOGGING_`.
  Variables names are split on underscores in the same way that system properties
  are split on the period character. As such, when executing
  `CMR_SOME_PROP_NAME=value lein run`, the configuration data will have the
  same nested data as show above, namely: `{:cmr {:some {:prop {:name "value"}}}}`.
  As with system property conifguration, environment variables that can be
  based as integers, are.


## Order of Precedence

1. Environment variables reign supreme: they override all other means of
   configuration.
1. Next are options set, e.g., via the command line with `java` or in `lein`'s
   `:jvm-opts`; these take precedence over entries made in the configuration
   file.
1. Finally, anything set in the configuration file that wasn't specified in the
   above will be interpreted.


## Data Structure

Once environment variables, JVM options system properties, and the EDN config
file have all been parsed, they are merged (using `deep merge`) into a hash map
something like the following:

```clj
 {:cmr
   {:opendap
   	 {:public
   	 	{:protocol ""
   	 	 :host ""
   	     :port nnn}
   	  :host ""
   	  :port nnn
   	  :relative
   	    {:root
   	      {:url ""}}
   	  :version "x.y.z"}}
   {:access
     {:control
     	{:protocol ""
     	 :host ""
     	 :port nnn}}}
   {:echo
     {:rest
     	{:protocol ""
     	 :host ""
     	 :port nnn
     	 :context ""}}}
   {:ingest
     {:protocol ""
 	  :host ""
 	  :port nnn}}
   {:search
     {:protocol ""
 	  :host ""
 	  :port nnn}}}
```

