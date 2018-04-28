```
╭───────╮                    ╭────╮
│    ╭──╯╭───╮╭───╮╭──────╮  ├────┤╭──────╮╭──────╮╭────┬─╮╭──────╮
│    ╰──╮├───┤│   ││  ──  │  │    ││   ╭──╯│   ╭╮ ││      ││  ────┤
│    ╭──╯│   ││   ││      │  │    ││   ╰──╮│   ││ ││   ╭╮ ││      │
│    │   │   ││   ││  ────┤  │    ││      ││   ╰╯ ││   ││ │├────  │
╰────╯   ╰───╯╰───╯╰──────╯  ╰────╯╰──────╯╰──────╯╰───╯╰─╯╰──────╯
╭─╮  ╭─╮  ┬─╮         ┬  ╭─╮  ┬  ┬  ╭─╮  ╭─╮  ╭─╮  ┬─╮  ┬  ╭─╮  ╭┬╮
├┤   │ │  ├┬╯         │  ├─┤  ╰╮╭╯  ├─┤  ╰─╮  │    ├┬╯  │  ├─╯   │ 
┴    ╰─╯  ┴╰─       ╰─╯  ┴ ┴   ╰╯   ┴ ┴  ╰─╯  ╰─╯  ┴╰─  ┴  ┴     ┴ 
```
> File specific icons for the browser from Atom File-icons, https://github.com/file-icons/atom

<img alt="Icon previews" width="850" src="https://raw.githubusercontent.com/file-icons/atom/6714706f268e257100e03c9eb52819cb97ad570b/preview.png" />

## Install

Use `npm` to install as follows, 

```bash
npm i websemantics/file-icons-js
```

Or, `Bower`, 

```bash
bower i websemantics/file-icons-js
```

## Getting Started

Include `css` styles from `css/style.css` in the header of an html document.

Get an instance of `FileIcons` class,

```js
var icons = window.FileIcons;
```

Get the class name of the icon that represent a filename (for example `text-icon`),

```js
var filename = 'src/app.js';
var class_name = icons.getClass(filename);
```

You can also get a class name of the associated icon color,

```js
var  filename = 'README.md';
var class_name = icons.getClassWithColor(filename);
```

Use the class name to generate html, for example,

```js
document.body.innerHTML = "<a><i class=" + class_name + "></i>$filename</a>";
```

## Resources

- [Atom File Icons](https://github.com/file-icons/atom), file specific icons for improved visual grepping.
- [Markdown Browser Plus](https://github.com/websemantics/markdown-browser-plus), Github flavoured, local file browser for markdown docs.

## Support

Need help or have a question? post at [StackOverflow](https://stackoverflow.com/questions/tagged/file-icons-js+websemantics).

*Please don't use the issue trackers for support/questions.*

*Star if you find this project useful, to show support or simply for being awesome :)*

## Contribution

Contributions to this project are accepted in the form of feedback, bugs reports and even better - pull requests.

## License

[MIT license](http://opensource.org/licenses/mit-license.php) Copyright (c) Web Semantics, Inc.
