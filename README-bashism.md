Running `./sbt.sh` emits `./sbt.sh: 2: ./sbt.sh: source: not found`
====

This is due to the usage of `source` within the scripts.

There are two potential fixes:

* Change `#!/bin/sh` to `#!/bin/bash`
* Change `source` to `.`

I'm doing the ladder one...

* `find . -name "*.sh*|xargs -n1 sed -i "s/source /. /"`
