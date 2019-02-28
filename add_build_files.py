    #!/bin/python

import os

def build_file_for(package_name):
  return  """package(default_visibility = ["//visibility:public"])

scala_library(
    name = "{}",
    srcs = glob(["*.java"]) + glob(["*.scala"]),
    runtime_deps = [
    ],
    deps = [
    ],
)
""".format(package_name)

build_files_paths = set()
for path, subdirs, files in os.walk("src"):
    for name in files:
        if ".scala" in name or ".java" in name:
            build_files_paths.add(os.path.join(path))

for path in build_files_paths:
    print(path)

    path_tokens = path.split("/")

    with open("{}/BUILD.bazel".format(path), "w+") as f:
        # print(build_file_for(path_tokens[-1]))
        f.write(build_file_for(path_tokens[-1]))

# for build_file in build_files_paths:
#     # print build_file
#     with open(build_file, "r+") as f:
#         print("reading file" + build_file)
#         newText = f.read()
#         for artifact in artifacts_to_remove:
#             genericPattern = "        \"{}\",\n"
#             newText = newText.replace(genericPattern.format("@" + artifact), "")
#             newText = newText.replace(genericPattern.format("@" + artifact + "//jar"), "")
#             newText = newText.replace(genericPattern.format("@" + artifact + "//:" + artifact), "")
#         f.seek(0)
#         f.write(newText)
#         f.truncate()
