def call() {
  pipeline {
    agent { label "agent-1" }
    parameters {
      booleanParam(
        name: 'WHEN_I_MET_YOU_IN_SUMMER',
        defaultValue: false,
        description: 'to my heartbeats sound. We fell in love as the leaves turned brown'
      )
    }
    options {
      buildDiscarder(logRotator(daysToKeepStr: '365', numToKeepStr: '64'))
    }
    stages {
      stage('Some stand') {
        steps {
          passParamsChild1()
        }
      }
    }
  }
}
