#!/bin/sh
D="$(dirname "$0")"
D="$(cd "${D}"; pwd)"
DD="$(dirname "${D}")"
(
  for f in "${D}/env.sh" "${D}/build.xml"; do
    if [ ! -s "${f}" ]; then
      echo >&2 "$0: Unable to access file '${f}'"
      exit 1
    fi
  done
  . "${D}/env.sh"
  cd "${DD}"
  ant -f "${D}/build.xml" all
)
