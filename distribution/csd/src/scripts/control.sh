#!/usr/bin/env bash

CMD=$1

DRUID_LIB=${CDH_DRUID_HOME}/lib/*
DRUID_ROLE_JVM_OPTS=${DRUID_ROLE_JVM_OPTS:-}
DRUID_ROLE_CFG_DIR=${CONF_DIR}/config
DRUID_ROLE_CFG_FILE=${DRUID_ROLE_CFG_DIR}/${DRUID_ROLE_CFG}
DRUID_ROLE_LOG_FILE=${DRUID_ROLE_CFG_DIR}/log4j2.yml

function log {
  timestamp=`date`
  echo "$timestamp: $1"	   #stdout
  echo "$timestamp: $1" 1>&2; #stderr
}

function substitute_role_cfg_file_tokens {
  sed -i -e "s#{{ZK_QUORUM}}#${ZK_QUORUM}#" ${DRUID_ROLE_CFG_FILE}
}

function substitute_log4j2_file_tokens {
  # Token replacement in aux-config log4j2.xml
  sed -i -e "s#{{LOG4J2_LOG_DIR}}#${LOG4J2_LOG_DIR}#" -e "s#{{LOG4J2_LOG_FILE}}#${LOG4J2_LOG_FILE}#" \
    -e "s#{{LOG4J2_THRESHOLD}}#${LOG4J2_THRESHOLD}#" -e "s#{{LOG4J2_MAX_SIZE}}#${LOG4J2_MAX_SIZE}#" \
    -e "s#{{LOG4J2_MAX_BACKUPS}}#${LOG4J2_MAX_BACKUPS}#" ${DRUID_ROLE_LOG_FILE}
}

function generate_realtime_spec_file {
    spec_file=${DRUID_ROLE_CFG_DIR}/${REALTIME_SPEC}
    prop_name="realtime.spec"
    python - <<END
from xml.etree import ElementTree
from xml.etree.ElementTree import Element
from xml.etree.ElementTree import SubElement
def getconfig(root, name):
        for existing_prop in root.getchildren():
                if existing_prop.find('name').text == name:
                        return existing_prop.find('value').text

conf = ElementTree.parse("$spec_file").getroot()
prop_value = getconfig(root = conf, name = "$prop_name")

conf_file = open("$spec_file",'w')
conf_file.write(prop_value)
conf_file.close()
END
    echo "$spec_file"
}


function link_hadoop_sites {
  HADOOP_CONF_DIR=${CONF_DIR}/hadoop-conf
  CORE_SITE=${DRUID_ROLE_CFG_DIR}/core-site.xml
  if [ -L ${CORE_SITE} ]; then
    rm -rf ${CORE_SITE}
  fi
  ln -s ${HADOOP_CONF_DIR}/core-site.xml ${CORE_SITE}

  HDFS_SITE=${DRUID_ROLE_CFG_DIR}/hdfs-site.xml
  if [ -L ${HDFS_SITE} ]; then
    rm -rf ${HDFS_SITE}
  fi
  ln -s ${HADOOP_CONF_DIR}/hdfs-site.xml ${HDFS_SITE}
}


ARGS=()

case $CMD in

  (start_coordinator)
    log "Startitng Druid Coordinator"
    substitute_role_cfg_file_tokens
    ARGS+=("server")
    ARGS+=("coordinator")
    ;;

  (start_broker)
    log "Startitng Druid Broker"
    substitute_role_cfg_file_tokens
    ARGS=("server")
    ARGS+=("broker")
    ;;

  (start_historical)
    log "Startitng Druid Historical"
    substitute_role_cfg_file_tokens
    link_hadoop_sites
    ARGS=("server")
    ARGS+=("historical")
    ;;

 (start_realtime)
    log "Startitng Druid Realtime"
    substitute_role_cfg_file_tokens
    link_hadoop_sites
    specFile=`generate_realtime_spec_file`
    DRUID_ROLE_JVM_OPTS="${DRUID_ROLE_JVM_OPTS} -Ddruid.realtime.specFile=${specFile}"
    ARGS=("server")
    ARGS+=("realtime")
    ;;

 (start_overlord)
    log "Startitng Druid Overlord"
    substitute_role_cfg_file_tokens
    ARGS=("server")
    ARGS+=("overlord")
    ;;

 (start_middleManager)
    log "Startitng Druid MiddleManager"
    substitute_role_cfg_file_tokens
    ARGS=("server")
    ARGS+=("middleManager")
    ;;

  (init_metadata)
    log "Initialize Metadata Storage"
    ARGS=("tools")
    ARGS+=("metadata-init")
    ARGS+=("--connectURI")
    ARGS+=($CONNECTOR_URI)
    ARGS+=("--user")
    ARGS+=($CONNECTOR_USER)
    ARGS+=("--password")
    ARGS+=($CONNECTOR_PASSWD)
    exit 0
    ;;

  (*)
    log "Don't understand [$CMD]"
    ;;

esac

# Debug info
echo "CDH_DRUID_HOME: ${CDH_DRUID_HOME}"
echo "CONF_DIR: ${CONF_DIR}"
echo "ZK_QUORUM: ${ZK_QUORUM}"
echo "DRUID_ROLE_CFG: ${DRUID_ROLE_CFG}"
echo "DRUID_ROLE_JVM_OPTS : ${DRUID_ROLE_JVM_OPTS}"
echo "ENV: `env`"

substitute_log4j2_file_tokens

JAVA=${JAVA_HOME}/bin/java
MAIN_CLASS="io.druid.cli.Main"
cmd="${JAVA} ${DRUID_ROLE_JVM_OPTS} -cp ${DRUID_ROLE_CFG_DIR}:${DRUID_LIB} ${MAIN_CLASS} ${ARGS[@]}"
echo "Run [$cmd]"
exec $cmd
