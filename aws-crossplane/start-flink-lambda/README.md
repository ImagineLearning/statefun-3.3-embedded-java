# Instructions to build the lambda handler Zip file
These instructions result in the creation of `start_flink_py.zip`
```
cd aws-crossplane/start-flink-lambda/
mkdir package
python3 -m venv venv.demo
source venv.demo/bin/activate
pip install --target ./package boto3
./build_start_flink_py_zip.sh
```
