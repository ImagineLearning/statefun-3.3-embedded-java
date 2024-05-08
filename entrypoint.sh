#!/usr/bin/env bash

set -e

export ENABLE_BUILT_IN_PLUGINS=flink-s3-fs-hadoop-1.18.1.jar

# fix for rocksb memory fragmentation issue
export LD_PRELOAD=$LD_PRELOAD:/usr/lib/x86_64-linux-gnu/libjemalloc.so

# If unspecified, the hostname of the container is taken as the JobManager address
#JOB_MANAGER_RPC_ADDRESS=${JOB_MANAGER_RPC_ADDRESS:-$(hostname -f)}
CONF_FILE="${FLINK_HOME}/conf/flink-conf.yaml"

# This allows us to run standalone-job for local development
if [ "$IS_LOCAL_DEV" = "true" ]
then
       cp ${FLINK_JOB_DIR}/flink-job.jar ${FLINK_HOME}/lib/flink-job.jar
       if [ -n "${FLINK_PROPERTIES}" ]; then
   	        echo "${FLINK_PROPERTIES}" >> "${CONF_FILE}"
       fi
fi


#######################################################################################
# What follows is a modified form of the official flink-docker::/docker-entrypoint.sh #
#######################################################################################

COMMAND_STANDALONE="standalone-job"

drop_privs_cmd() {
    if [ $(id -u) != 0 ]; then
        # Don't need to drop privs if EUID != 0
        return
    elif [ -x /sbin/su-exec ]; then
        # Alpine
        echo su-exec flink
    else
        # Others
        echo gosu flink
    fi
}

copy_plugins_if_required() {
  if [ -z "$ENABLE_BUILT_IN_PLUGINS" ]; then
    return 0
  fi

  echo "Enabling required built-in plugins"
  for target_plugin in $(echo "$ENABLE_BUILT_IN_PLUGINS" | tr ';' ' '); do
    echo "Linking ${target_plugin} to plugin directory"
    plugin_name=${target_plugin%.jar}

    mkdir -p "${FLINK_HOME}/plugins/${plugin_name}"
    if [ ! -e "${FLINK_HOME}/opt/${target_plugin}" ]; then
      echo "Plugin ${target_plugin} does not exist. Exiting."
      exit 1
    else
      ln -fs "${FLINK_HOME}/opt/${target_plugin}" "${FLINK_HOME}/plugins/${plugin_name}"
      echo "Successfully enabled ${target_plugin}"
    fi
  done
}


prepare_job_manager_start() {
    echo "Starting Job Manager"
    copy_plugins_if_required
}

if [ "$1" = "help" ]; then
    echo "Usage: $(basename "$0") (jobmanager|${COMMAND_STANDALONE}|taskmanager|help)"
    exit 0
elif [ "$1" = "jobmanager" ]; then
    shift 1
    prepare_job_manager_start

    exec $(drop_privs_cmd) "$FLINK_HOME/bin/jobmanager.sh" start-foreground "$@"
elif [ "$1" = ${COMMAND_STANDALONE} ]; then
    shift 1
    prepare_job_manager_start

    exec $(drop_privs_cmd) "$FLINK_HOME/bin/standalone-job.sh" start-foreground -D"classloader.parent-first-patterns.additional=org.apache.flink.statefun;org.apache.kafka;com.google.protobuf" "$@"
elif [ "$1" = "taskmanager" ]; then
    shift 1
    echo "Starting Task Manager"
    copy_plugins_if_required

    TASK_MANAGER_NUMBER_OF_TASK_SLOTS=${TASK_MANAGER_NUMBER_OF_TASK_SLOTS:-$(grep -c ^processor /proc/cpuinfo)}

    exec $(drop_privs_cmd) "$FLINK_HOME/bin/taskmanager.sh" start-foreground -D"classloader.parent-first-patterns.additional=org.apache.flink.statefun;org.apache.kafka;com.google.protobuf" "$@"
fi

exec "$@"
