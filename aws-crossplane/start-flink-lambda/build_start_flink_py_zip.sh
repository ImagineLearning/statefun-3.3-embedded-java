#! /bin/bash

set -e

cd $(dirname $0)
rm -rf start_flink_py.zip
cd package
zip -r ../start_flink_py.zip .
cd ..
zip start_flink_py.zip start_flink.py

