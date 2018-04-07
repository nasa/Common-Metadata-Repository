# cmr-opendap

*OPeNDAP Integration in the CMR*

[![][logo]][logo]


#### Contents

* [About](#about-)
* [Dependencies](#dependencies-)
* [Running the Tests](#running-the-tests-)
* [Documentation](#documentation-)
  * [Quick Start](#quick-start-)
  * [Project Guides](#project-guides-)
  * [Reference](#reference-)
* [License](#license-)


## About [&#x219F;](#contents)

TBD


## Dependencies [&#x219F;](#contents)

* Java
* `lein`


## Running the Tests [&#x219F;](#contents)

To run just the unit tests, use this command:

```
$ lein ltest :unit
```

Similarly, for just the integration tests:

```
$ lein ltest :integration
```

The default behaviour of `lein ltest` runs all tests types.


## Documentation [&#x219F;](#contents)

### Quick Start [&#x219F;](#contents)

With dependencies installed and repo cloned, switch to the project directory
and start the REPL:

```
$ lein repl
```

Then bring up the system:

```clj
(startup)
```

When done:

```clj
(shutdown)
```


### Project Guides [&#x219F;](#contents)

TBD


### Project Reference [&#x219F;](#contents)

* [API Reference][api-docs]
* [Marginalia][marginalia-docs]


### Related Resources

TBD


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
[api-docs]: http://cmr-exchange.github.io/cmr-opendap/current/
[marginalia-docs]: http://cmr-exchange.github.io/cmr-opendap/current/marginalia.html
[setup-docs]: http://cmr-exchange.github.io/cmr-opendap/current/0500-setup.html
[connecting-docs]: http://cmr-exchange.github.io/cmr-opendap/current/0750-connecting.html
[usage-docs]: http://cmr-exchange.github.io/cmr-opendap/current/1000-usage.html
[dev-docs]: http://cmr-exchange.github.io/cmr-opendap/current/2000-dev.html
