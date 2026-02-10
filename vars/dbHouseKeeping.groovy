def call() {
  pipeline {
    agent { label 'master' }

    stages {
      stage('Run pyscript') {
        steps {
          script {

            def pyScript = libraryResource('clear_table.py')
            def requirements = libraryResource('requirements.txt')

            writeFile file: 'clear_table.py', text: pyScript
            writeFile file: 'requirements.txt', text: requirements

            withCredentials([[
              $class: 'UsernamePasswordMultiBinding',
              credentialsId: 'PSQL',
              usernameVariable: 'PSQL_MQ_USER',
              passwordVariable: 'PSQL_MQ_PASSWORD'
            ]]) {
              withPythonEnv("python3") {
                sh 'pip install -r requirements.txt'
                sh 'python3 clear_table.py'
              }
            }
          }
        }
      }
    }
  }
}

