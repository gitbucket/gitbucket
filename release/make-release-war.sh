#!/bin/sh
D="$(dirname "$0")"
D="$(cd "${D}"; pwd)"
DD="$(dirname "${D}")"
(
  for f in "${DD}/env.sh" "${D}/build.xml"; do
    if [ ! -s "${f}" ]; then
      echo >&2 "$0: Unable to access file '${f}'"
      exit 1
    fi
  done
  cd "${DD}"
  . "${DD}/env.sh"
  ant -f "${D}/build.xml" all
)
