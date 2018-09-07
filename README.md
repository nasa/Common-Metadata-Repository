# cmr-jar-plugin

*A library for creating JAR-based plugins*

[![][logo]][logo]


#### Contents

* [About](#about-)
* [Dependencies](#dependencies-)
* [Setup](#setup-)
* [Usage](#usage-)
* [License](#license-)


## About [&#x219F;](#contents)

This project offers two key pieces of fucntionality:

1. An easy means of treating regular JAR files as application-specific
   plugins, and
1. Using the [Component](https://github.com/stuartsierra/component)
   library to register these in a running system.


## Dependencies [&#x219F;](#contents)

* Java
* `lein`


## Setup [&#x219F;](#contents)

Before using, one must do two things:

* Create one or more plugin JAR files (requires adding custom `MANIFEST.mf`
  headers), and
* Add (or update) a configuration file in your project's resource path.


### Creating a Plugin Jar

The `lein` tool provides an
[easy and intuitive way](https://github.com/technomancy/leiningen/blob/master/sample.project.clj)
of adding custom entries to a JAR file's manifest file:

```clj
  :manifest {"CMR-Plugin" "service-bridge-routes"}
```

Here we're annotating a project as a `CMR-Plugin` providing a plugin type of
`service-bridge-routes`.


### Creating a Configuration File

Next, you need to create a configuration file in EDN format. Here is the
default config file:

```clj
{:plugin {
   :jarfiles {
     ;; The reducer factory function must take two args: plugin name and plugin
     ;; type -- both of type ^String.
     :reducer-factory cmr.plugin.jar.core/create-regex-plugin-reducer}
   :registry {
     :component-key :plugin
     :default {
       :plugin-name ".*"
       :plugin-type ".*"
       ;; The fowllowing is an in-JAR path to the config file
       :config-file "config/cmr-opendap/config.edn"}
     :web {
       :route-keys [:web :route-fns]
       :api-route-key :api
       :site-route-key :site}}}}
```

You can put this function anywhere in your project that will be included in
the JAR file.


Note that the `:config-file` that is referneced in the configuration is where
the JVM will look in the plugin JAR for your _plugin's_ configuration file.
It is (sanely) expected that all of your plugins will share a common path and
file name.

While this project was originally designed for use by the NASA/Earthdata's
CMR suite of services, it can be used anywhere with any name. Names can be
either strings or (Clojure-style) regular expressions. The same is true for the
plugin type. Note, however, that the closer a match you provide, the quicker
the plugins will be processed (too library a regular expression will let
through all the JAR files in your project's classpath, and each of those will
be searched for the plugin config file.)

`cmr-jar-plugin` comes with a handful of reducer-creating functions, the most
flexible of which is the one in the default configuration. These functions
take as arguments a plugin name and a plugin type (e.g., the key and value that
are used in a manifest file). They return a reducer function (a function
whose first arg is an accumulator and whose second argument is the item being
processed in any given step). You can create your own if you so desire, and
reference it in your configuration file.

Once the reducer has created a collection of JAR files that match what you
specified in your configuration, they will then read each plugin's configuration
file (stored in the JAR file at the location you indicated with
`:config-file`).

By default, a web/routes plugin expects to find your routes configuration at
`[:web :route-fns]`:

```clj
{:web {
   :route-fns {
   	 :api my.plugin1.app.api.routes/all
   	 :site my.plugin1.app.site.routes/all}}}
```

If your web plugin's routes are configured elsewhere, just update the
`:route-keys` entry in your confiuration. Also, the keynames used for API and
site routes can be changed.

Finally, all that's left' to be done is `lein install` or `lein deploy clojars`
(depending upon where you want your plugin dep to live).


## Usage [&#x219F;](#contents)

### Library

TBD

### Registry

TBD


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
