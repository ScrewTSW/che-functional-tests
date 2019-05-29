import groovy.json.JsonOutput
import groovy.json.JsonParser

def silent_sh(cmd) {
    sh('#!/bin/sh -e\n' + cmd)
}

pipeline {
    agent { label 'osioperf-master1' }
    environment {
        ZABBIX_USER_PASSWORD_CREDENTIALS = credentials('${ZABBIX_CREDENTIALS_ID}')
        ZABBIX_REPORTER_USER = ""
        ZABBIX_REPORTER_PASSWORD = ""
    }
    stages {
        stage ("Getting zabbix access token") {
            steps {
                script {
                    zabbix_user_password_credentials_array = ZABBIX_USER_PASSWORD_CREDENTIALS.split(":")
                    ZABBIX_REPORTER_USER = zabbix_user_password_credentials_array[0]
                    ZABBIX_REPORTER_PASSWORD = zabbix_user_password_credentials_array[1]
                }
            }
        }
    }
}