#!/bin/bash

pushd .
cd ${project.build.directory}

parcel_name="${project.artifactId}-${druid.version}"
mkdir $parcel_name
decompressed_dir="extract"

#jdk_download_url="http://download.oracle.com/otn-pub/java/jdk/${jdk.version}-${jdk.build}/jdk-${jdk.version}-linux-x64.tar.gz"
#jdk_download_name="jdk.tar.gz"
#curl -L -o $jdk_download_name -H "Cookie: oraclelicense=accept-securebackup-cookie" $jdk_download_url
#mkdir $decompressed_dir
#tar xzf $jdk_download_name -C $decompressed_dir
#mv $decompressed_dir/$(\ls $decompressed_dir) $parcel_name/jdk
#rm -rf $decompressed_dir


druid_download_name="druid.tar.gz"
#druid_download_url="http://static.druid.io/artifacts/releases/druid-${druid.version}-bin.tar.gz"
druid_download_url="http://127.0.0.1:8000/druid-${druid.version}-bin.tar.gz"

curl -L -o $druid_download_name $druid_download_url
mkdir $decompressed_dir
tar xzf $druid_download_name -C $decompressed_dir

druid_dir=`\ls $decompressed_dir`
for file in `\ls $decompressed_dir/$druid_dir`; do
  mv $decompressed_dir/$druid_dir/$file $parcel_name
done
rm -rf $decompressed_dir



cat <<"EOF" > ${parcel_name}/druid
#!/usr/bin/env python

import os
import sys
import subprocess
from os.path import realpath, dirname

path = dirname(realpath(sys.argv[0]))
args = ' '.join(sys.argv[1:])
role_cfg_dir = os.getenv('DRUID_ROLE_CFG_DIR', "")
n = len(sys.argv[1:])

if n == 0:
  args = "help"
elif n > 1:
  if role_cfg_dir == "" and (sys.argv[1] == "server" or sys.argv[1] == "example"):
    role_cfg_dir = "%s/config/_common:%s/config/%s" % (path, path, sys.argv[2])

role_jvm_opts = os.getenv('DRUID_ROLE_JVM_OPTS', "-Xmx512m -Duser.timezone=UTC -Dfile.encoding=UTF-8")
role_classpath = "%s:%s/lib/*" % (role_cfg_dir, path)

cmd = "java %s -classpath %s io.druid.cli.Main %s" % (role_jvm_opts, role_classpath, args)
print cmd
subprocess.call(cmd, shell=True)
EOF
chmod +x ${parcel_name}/druid


cp -a ${project.build.outputDirectory}/meta ${parcel_name}
tar zcf ${parcel_name}.parcel ${parcel_name}/ --owner=root --group=root

mkdir repository
for i in el5 el6 sles11 lucid precise squeeze wheezy; do
  cp ${parcel_name}.parcel repository/${parcel_name}-${i}.parcel
done

cd repository
curl https://raw.githubusercontent.com/cloudera/cm_ext/master/make_manifest/make_manifest.py | python

popd
