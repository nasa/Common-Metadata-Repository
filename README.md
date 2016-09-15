# codox-theme-rdash

This is a modern and responsive [codox][codox] theme, partially inspired by the
styling of [RDash UI][rdash].

Note that this needs codox ≥ 0.10.0.

[codox]: https://github.com/weavejester/codox
[rdash]: http://rdash.github.io/

## Screenshots

<img src='screenshots/rdash.png' alt='Codox + RDash' height='250' />
<img src='screenshots/rdash-responsive.png' alt='Codox + RDash (responsive)' height='250' />

## Usage

Add the following dependency to your `project.clj`:

TODO

Then set the following:

```clojure
:codox {:theme :rdash}
```

This theme will enable Markdown rendering for docstrings by default.

## License

Copyright &copy; 2016 Yannick Scherer

This project is licensed under the [Eclipse Public License 1.0][license].

[license]: https://www.eclipse.org/legal/epl-v10.html

## Derivative

This theme is based on the codox default assets licensed under the
[Eclipse Public License v1.0][epl]:

Copyright &copy; 2016 James Reeves

[epl]: http://www.eclipse.org/legal/epl-v10.html
