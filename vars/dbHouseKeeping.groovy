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

              sh '''
                set -e

                python3 -m venv .venv
                . .venv/bin/activate

                pip install --upgrade pip
                pip install -r requirements.txt

                python clear_table.py
              '''
            }
          }
        }
      }
    }
  }
}

