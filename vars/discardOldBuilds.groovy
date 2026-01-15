def call(){
  pipeline {
    options {
      buildDiscarder(logRotator(daysToKeepStr: '365', numToKeepStr: '30'))
    }
    agent { label "ansible" }
    stages {
      stage("Example") {
        steps{
          sh 'echo Hello Captain'
        }
      }
    }
  }
}
